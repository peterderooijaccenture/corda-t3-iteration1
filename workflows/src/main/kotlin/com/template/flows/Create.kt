package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.ToDoItemContract
import com.template.states.StatusState
import com.template.states.ToDoItem
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
class CreateToDoItem(
        private val task: String
) : FlowLogic<ToDoItem>() {
    @Suspendable
    override fun call(): ToDoItem {
        val creator = ourIdentity
        val item = ToDoItem(assignedBy = creator, assignedTo = creator, task = task)
        val refState = serviceHub.vaultService.queryBy<StatusState>().states.first { it.state.data.open }
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