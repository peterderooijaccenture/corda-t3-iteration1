package com.template.contracts

import com.template.states.StatusState
import com.template.states.ToDoItem
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class StatusStateContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.contracts.StatusStateContract"
    }

    override fun verify(tx: LedgerTransaction) {
        require(tx.commands.size == 1) { "there must be exactly one command" }
        val command = tx.commands.single()
        when (command.value) {
            is Commands.Create -> {
                require(tx.inputStates.isEmpty()) { "there must be no inputs for a Create" }
                require(tx.outputStates.size == 1) { "there must be exactly one output for Create" }
            }
            else -> {
                throw IllegalArgumentException("unknown command ${command.value}")
            }
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Create : Commands
    }
}