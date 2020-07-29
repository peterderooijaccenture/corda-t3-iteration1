package com.template.contracts

import com.template.states.ToDoItem
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class ToDoItemContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.contracts.TemplateContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        require(tx.commands.size == 1) { "there must be exactly one command" }
        val command = tx.commands.single()
        when (command.value) {
            is Commands.Create -> {
                require(tx.inputStates.isEmpty()) { "there must be no inputs for a Create" }
                require(tx.outputStates.size == 1) { "there must be exactly one output for Create" }
                val toDoItem = tx.outputStates[0] as ToDoItem
                require(toDoItem.assignedBy == toDoItem.assignedTo) { "Create must self-assign the ToDoItem" }
                require(toDoItem.taskDescription.isNotEmpty()) { "taksDescription must not be empty" }
                require(toDoItem.taskDescription.length <= 40) { "taksDescription must be at most 40 characters long" }
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