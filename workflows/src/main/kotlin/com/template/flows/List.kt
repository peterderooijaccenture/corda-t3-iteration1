package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.states.ToDoItem
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy

@StartableByRPC
class ListToDoItems : FlowLogic<List<ToDoItem>>() {
    @Suspendable
    override fun call() = serviceHub.vaultService.queryBy<ToDoItem>().states.map { it.state.data }
}