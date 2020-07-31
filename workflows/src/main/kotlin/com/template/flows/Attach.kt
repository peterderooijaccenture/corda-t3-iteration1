package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.ToDoItemContract
import com.template.states.ToDoItem
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import java.io.BufferedInputStream
import java.net.URL

@StartableByRPC
@InitiatingFlow
class AttachFlow(
        private val linearId: UniqueIdentifier,
        private val file: String,   // e.g., "file:<path to ZIP from node root directory>"
        private val uploader: String

) : FlowLogic<ToDoItem>() {
    companion object {
        object IMPORT_ATTACHMENT : Step("Importing Attachment")
        object GET_TODO : Step("Getting the ToDoItem by LinearId")
        object BUILD_TRANSACTION : Step("Building the transaction")
        object SIGN_TRANSACTION : Step("Signing the transaction")
        object VERIFY_TRANSACTION : Step("Verifying the transaction")
        object COLLECT_SIGNATURES : Step("Collecting counterparty signatures") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALISE : Step("Finalising the transaction") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                IMPORT_ATTACHMENT,
                GET_TODO,
                BUILD_TRANSACTION,
                SIGN_TRANSACTION,
                VERIFY_TRANSACTION,
                COLLECT_SIGNATURES,
                FINALISE
        )
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): ToDoItem {
        progressTracker.currentStep = IMPORT_ATTACHMENT
        val inputStream = BufferedInputStream(URL(file).openStream())
        val attachmentId = serviceHub.attachments.importAttachment(inputStream, uploader, file)
        inputStream.close()

        progressTracker.currentStep = GET_TODO
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val inputState = serviceHub.vaultService.queryBy<ToDoItem>(queryCriteria).states.single()
        val inputItem = inputState.state.data
        val toDoItem = inputState.state.data.withAttachment(attachmentId)

        progressTracker.currentStep = BUILD_TRANSACTION
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val transactionBuilder = TransactionBuilder(notary)
                .addInputState(inputState)
                .addCommand(
                        data = ToDoItemContract.Commands.Attach(),
                        keys = listOf(inputItem.assignedTo.owningKey, inputItem.assignedBy.owningKey).distinct()
                )
                .addOutputState(toDoItem)
                .addAttachment(attachmentId)

        progressTracker.currentStep = VERIFY_TRANSACTION
        transactionBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGN_TRANSACTION
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)

        if (inputState.state.data.assignedBy == ourIdentity) {
            progressTracker.currentStep = FINALISE
            subFlow(FinalityFlow(signedTransaction, emptyList(), FINALISE.childProgressTracker()))
        }
        else {
            progressTracker.currentStep = COLLECT_SIGNATURES
            val session = initiateFlow(inputState.state.data.assignedBy)
            val counterSignedTransaction = subFlow(CollectSignaturesFlow(
                    signedTransaction, listOf(session), COLLECT_SIGNATURES.childProgressTracker())
            )
            progressTracker.currentStep = FINALISE
            subFlow(FinalityFlow(counterSignedTransaction, listOf(session), FINALISE.childProgressTracker()))
        }
        return toDoItem
    }
}

@InitiatedBy(AttachFlow::class)
class AttachFlowResponder(val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        if (session.counterparty == ourIdentity)
            return

        val signTransactionFlow = object : SignTransactionFlow(session) {
            @Suspendable
            override fun checkTransaction(stx: SignedTransaction) {
                println("accepting attachment in ToDoItem ${stx.tx.outputsOfType<ToDoItem>().single()}")
            }
        }
        val txId = subFlow(signTransactionFlow).id

        val signedTransaction = subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
        val newToDoItem = signedTransaction.tx.outputsOfType<ToDoItem>().single()
        println("updated ToDoItem: $newToDoItem")
    }
}