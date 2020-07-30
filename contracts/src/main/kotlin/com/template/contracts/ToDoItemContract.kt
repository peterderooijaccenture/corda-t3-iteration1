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
        const val ID = "com.template.contracts.ToDoItemContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        require(tx.commands.size == 1) { "there must be exactly one command" }
        val command = tx.commands.single()
        val signers = tx.commands[0].signers
        when (command.value) {
            is Commands.Create -> {
                require(tx.inputStates.isEmpty()) { "there must be no inputs for a Create" }
                require(tx.outputStates.size == 1) { "there must be exactly one output for Create" }
                val toDoItem = tx.outputStates[0] as ToDoItem
                require(toDoItem.assignedBy == toDoItem.assignedTo) { "Create must self-assign the ToDoItem" }
                require(toDoItem.taskDescription.isNotEmpty()) { "taksDescription must not be empty" }
                require(toDoItem.taskDescription.length <= 40) { "taksDescription must be at most 40 characters long" }
                require(signers.contains(toDoItem.assignedBy.owningKey)) { "creator must sign ToDoItem Create" }
            }
            is Commands.Assign -> {
                require(tx.inputStates.size == 1) { "there must be no inputs for a Assign" }
                require(tx.outputStates.size == 1) { "there must be exactly one output for Assign" }
                val before = tx.inputStates[0] as ToDoItem
                val after = tx.outputStates[0] as ToDoItem
                require(before.taskDescription.contentEquals(after.taskDescription)) { "taskDescription must not change due to Assign" }
                require(before.dateOfCreation == after.dateOfCreation) { "creation date must not change due to Assign" }
                require(before.linearId == after.linearId) { "linearId must not change due to Assign" }
                require(before.assignedTo != after.assignedTo) { "Assign must change assignedTo" }
                require(before.assignedTo == after.assignedBy) { "only assignee can Assign to another party" }
                require(signers.contains(before.assignedTo.owningKey)) { "original assignee must sign re-assignment" }
                require(signers.contains(after.assignedTo.owningKey)) { "new assignee must sign re-assignment" }
                require(after.deadLine != null) { "there must be a deadline when Assigning a ToDoItem" }
            }
            else -> {
                throw IllegalArgumentException("unknown command ${command.value}")
            }
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Create : Commands
        class Assign : Commands
    }
}