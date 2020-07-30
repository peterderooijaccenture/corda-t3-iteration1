package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.StatusStateContract
import com.template.contracts.ToDoItemContract
import com.template.states.StatusState
import com.template.states.ToDoItem
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// *********
// * Flows *
// *********

@StartableByRPC
class CreateToDoItem(
        private val taskDescription: String
) : FlowLogic<ToDoItem>() {
    @Suspendable
    override fun call(): ToDoItem {
        val creator = ourIdentity
        val item = ToDoItem(assignedBy = creator, assignedTo = creator, taskDescription = taskDescription)
        val refState = serviceHub.vaultService.queryBy<StatusState>().states
                .filter { it.state.data.open }
                .first()
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val transactionBuilder = TransactionBuilder(notary)
                .addCommand(data = ToDoItemContract.Commands.Create(), keys = listOf(ourIdentity.owningKey))
                .addOutputState(item)
                .addReferenceState(ReferencedStateAndRef(refState))
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)
        subFlow(FinalityFlow(signedTransaction, emptyList()))
        return item
    }
}

@InitiatingFlow
@StartableByRPC
class AssignInitiator(
        private val linearId: UniqueIdentifier,
        private val assignee: Party,
        private val deadLine: LocalDate,
        private val expiresInSeconds: Long
) : FlowLogic<ToDoItem>() {
    @Suspendable
    override fun call() : ToDoItem {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val original = serviceHub.vaultService.queryBy<ToDoItem>(queryCriteria).states.single()
        require(original.state.data.assignedTo == ourIdentity) { "Assign must be called by the assignedTo Party" }
        val update = ToDoItem(
                assignedBy = ourIdentity,
                assignedTo = assignee,
                taskDescription = original.state.data.taskDescription,
                deadLine = deadLine,
                linearId = original.state.data.linearId
        )
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val transactionBuilder = TransactionBuilder(notary)
                .addCommand(
                        data = ToDoItemContract.Commands.Assign(),
                        keys = listOf(ourIdentity.owningKey, assignee.owningKey)
                )
                .addInputState(original)
                .addOutputState(update)
                .setTimeWindow(TimeWindow.untilOnly(Instant.now().plusSeconds(expiresInSeconds)))
        transactionBuilder.verify(serviceHub)
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)
        val session = initiateFlow(assignee)
        val counterSignedTransaction = subFlow(CollectSignaturesFlow(signedTransaction, listOf(session)))
        subFlow(FinalityFlow(counterSignedTransaction, listOf(session)))
        return update
    }
}

@InitiatedBy(AssignInitiator::class)
class AssignResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                println("wait 10 seconds...")
                sleep(Duration.ofSeconds(10))
                val toDoItem = stx.tx.outputsOfType<ToDoItem>().single()
                require(toDoItem.assignedTo == ourIdentity) { "the task is assiend to me" }
                require(toDoItem.deadLine!!.isAfter(LocalDate.now().plusDays(3))) { "the deadline must be at least 3 days away" }
                println("accepting new ToDoItem $toDoItem")
            }
        }
        val txId = subFlow(signTransactionFlow).id
        val signedTransaction = subFlow(ReceiveFinalityFlow(counterpartySession, expectedTxId = txId))
        val newToDoItem = signedTransaction.tx.outputsOfType<ToDoItem>().single()
        println("accepted new ToDoItem: $newToDoItem")
        return signedTransaction
    }
}

@StartableByRPC
class ListToDoItems() : FlowLogic<List<ToDoItem>>() {
    @Suspendable
    override fun call() = serviceHub.vaultService.queryBy<ToDoItem>().states.map { it.state.data }
}

@StartableByRPC
class CreateReferenceStates(
        private val open: Boolean
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val refState = StatusState(open, listOf(ourIdentity))
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val transactionBuilder = TransactionBuilder(notary)
                .addCommand(
                        data = StatusStateContract.Commands.Create(),
                        keys = listOf(ourIdentity.owningKey)
                )
                .addOutputState(refState)
        transactionBuilder.verify(serviceHub)
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)
        println("ReferenceState ${refState.linearId}")
        return subFlow(FinalityFlow(signedTransaction, emptyList()))
    }
}

//@StartableByRPC
//@InitiatingFlow
//class AttachToToDoFlow(
//        private val linearId: UniqueIdentifier
//
//) : FlowLogic<SignedTransaction>() {
//    @Suspendable
//    override fun call(): SignedTransaction {
//        try {
//            val inputStream = BufferedInputStream(URL("file.txt").openStream())
//            val secureHash =
//        }
//    }
//}