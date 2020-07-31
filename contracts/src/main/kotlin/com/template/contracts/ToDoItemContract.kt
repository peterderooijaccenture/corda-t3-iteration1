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
                val toDoItem = tx.outputsOfType<ToDoItem>().single()
                require(toDoItem.assignedBy == toDoItem.assignedTo) { "Create must self-assign the ToDoItem" }
                require(toDoItem.task.isNotEmpty()) { "taksDescription must not be empty" }
                require(toDoItem.task.length <= 40) { "taksDescription must be at most 40 characters long" }
                require(signers.contains(toDoItem.assignedBy.owningKey)) { "creator must sign ToDoItem Create" }
            }
            is Commands.Assign -> {
                require(tx.inputStates.size == 1) { "there must be exactly one input for an Assign" }
                require(tx.outputStates.size == 1) { "there must be exactly one output for an Assign" }
                val before = tx.inputsOfType<ToDoItem>().single()
                val after = tx.outputsOfType<ToDoItem>().single()
                require(before.task.contentEquals(after.task)) { "taskDescription must not change due to Assign" }
                require(before.dateOfCreation == after.dateOfCreation) { "creation date must not change due to Assign" }
                require(before.linearId == after.linearId) { "linearId must not change due to Assign" }
                require(before.assignedTo != after.assignedTo) { "Assign must change assignedTo" }
                require(before.assignedTo == after.assignedBy) { "only assignee can Assign to another party" }
                require(signers.contains(before.assignedTo.owningKey)) { "original assignee must sign re-assignment" }
                require(signers.contains(after.assignedTo.owningKey)) { "new assignee must sign re-assignment" }
                require(after.deadline != null) { "there must be a deadline when Assigning a ToDoItem" }
            }
            is Commands.Attach -> {
                require(tx.inputStates.size == 1) { "there must be exactly one input for an Attach" }
                require(tx.outputStates.size == 1) { "there must be exactly one output for an Attach" }
                val before = tx.inputsOfType<ToDoItem>().single()
                val after = tx.outputsOfType<ToDoItem>().single()
                require(before.task.contentEquals(after.task)) { "taskDescription must not change due to Attach" }
                require(before.dateOfCreation == after.dateOfCreation) { "creation date must not change due to Attach" }
                require(before.linearId == after.linearId) { "linearId must not change due to Attach" }
                require(before.assignedTo == after.assignedTo) { "Assign must not change assignedTo" }
                require(before.assignedBy == after.assignedBy) { "Assign must nbot change assignedBy" }
                require(signers.contains(before.assignedTo.owningKey)) { "assignee must sign Attach" }
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
        class Attach : Commands
    }
}