package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.ToDoItemContract
import com.template.states.ToDoItem
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.time.LocalDate

// *********
// * Flows *
// *********

@StartableByRPC
class CreateToDoItem(
        private val taskDescription: String
) : FlowLogic<ToDoItem>() {
    override fun call(): ToDoItem {
        val creator = ourIdentity
        val item = ToDoItem(assignedBy = creator, assignedTo = creator, taskDescription = taskDescription)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val transactionBuilder = TransactionBuilder(notary)
                .addCommand(data = ToDoItemContract.Commands.Create(), keys = listOf(ourIdentity.owningKey))
                .addOutputState(item)
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
        private val deadLine: LocalDate
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