package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.ToDoItemContract
import com.template.states.ToDoItem
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

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
        val sTx = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)
        subFlow(FinalityFlow(sTx, emptyList()))
        return item
    }
}

@InitiatingFlow
@StartableByRPC
class Initiator : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() {
        // Initiator flow logic goes here.
    }
}

@InitiatedBy(Initiator::class)
class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Responder flow logic goes here.
    }
}
