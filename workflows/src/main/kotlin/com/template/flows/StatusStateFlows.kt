package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.StatusStateContract
import com.template.states.StatusState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
class CreateReferenceStates(private val open: Boolean) : FlowLogic<StatusState>() {
    @Suspendable
    override fun call(): StatusState {
        val refState = StatusState(open, listOf(ourIdentity))
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val transactionBuilder = TransactionBuilder(notary)
                .addCommand(data = StatusStateContract.Commands.Create(), keys = listOf(ourIdentity.owningKey))
                .addOutputState(refState)
        transactionBuilder.verify(serviceHub)
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)
        println("ReferenceState ${refState.linearId} - open: ${refState.open}")
        subFlow(FinalityFlow(signedTransaction, emptyList()))
        return refState
    }
}