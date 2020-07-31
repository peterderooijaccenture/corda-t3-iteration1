package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.ToDoItemContract
import com.template.states.ToDoItem
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate


@InitiatingFlow
@StartableByRPC
class AssignFlow(
        private val linearId: UniqueIdentifier,
        private val assignee: Party,
        private val deadline: LocalDate,
        private val expires: Long = 20L
) : FlowLogic<ToDoItem>() {
    @Suspendable
    override fun call() : ToDoItem {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val original = serviceHub.vaultService.queryBy<ToDoItem>(queryCriteria).states.single()
        require(original.state.data.assignedTo == ourIdentity) { "Assign must be called by the assignedTo Party" }
        val update = original.state.data.withAssignee(assignee).withDeadline(deadline)

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val transactionBuilder = TransactionBuilder(notary)
                .addInputState(original)
                .addCommand(
                        data = ToDoItemContract.Commands.Assign(),
                        keys = listOf(ourIdentity.owningKey, assignee.owningKey)
                )
                .addOutputState(update)
                .setTimeWindow(TimeWindow.untilOnly(Instant.now().plusSeconds(expires)))
        transactionBuilder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)
        val session = initiateFlow(assignee)
        val counterSignedTransaction = subFlow(CollectSignaturesFlow(signedTransaction, listOf(session)))
        subFlow(FinalityFlow(counterSignedTransaction, listOf(session)))
        return update
    }
}

@InitiatedBy(AssignFlow::class)
class AssignFlowResponder(val session: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(session) {
            @Suspendable
            override fun checkTransaction(stx: SignedTransaction) {
                println("wait 10 seconds...")
                sleep(Duration.ofSeconds(10))
                val toDoItem = stx.tx.outputsOfType<ToDoItem>().single()
                require(toDoItem.assignedTo == ourIdentity) { "the task is assiend to me" }
                require(toDoItem.deadline!!.isAfter(LocalDate.now().plusDays(3))) { "the deadline must be at least 3 days away" }
                println("accepting new ToDoItem $toDoItem")
            }
        }
        val txId = subFlow(signTransactionFlow).id
        val signedTransaction = subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
        val newToDoItem = signedTransaction.tx.outputsOfType<ToDoItem>().single()
        println("accepted new ToDoItem: $newToDoItem")
        return signedTransaction
    }
}