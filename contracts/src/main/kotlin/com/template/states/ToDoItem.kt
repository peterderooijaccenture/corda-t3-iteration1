package com.template.states

import com.template.contracts.ToDoItemContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import java.util.*

@BelongsToContract(ToDoItemContract::class)
class ToDoItem(
        val assignedBy: Party,
        val assignedTo: Party,
        val task: String,
        val status: Status = Status.OPEN,
        val deadline: LocalDate? = null,
        val attachmentId: SecureHash? = null,
        override val linearId: UniqueIdentifier = UniqueIdentifier(id = UUID.randomUUID()),
        val dateOfCreation: LocalDate = LocalDate.now()
) : LinearState {

    @CordaSerializable
    enum class Status { OPEN, CLOSED }

    override val participants: List<AbstractParty>
        get() = listOf(assignedTo, assignedBy)

    override fun toString() = "ToDoItem $linearId: [$status] \"$task\" assigned to $assignedTo by $assignedBy" +
            (if (attachmentId!= null) "attachment: $attachmentId" else "")

    fun withAssignee(assignee: Party) = ToDoItem(
            assignedBy = this.assignedTo,
            assignedTo = assignee,
            task = this.task,
            deadline = this.deadline,
            linearId = this.linearId,
            attachmentId = attachmentId,
            dateOfCreation = this.dateOfCreation
    )

    fun withDeadline(deadline: LocalDate) = ToDoItem(
            assignedBy = this.assignedTo,
            assignedTo = this.assignedTo,
            task = this.task,
            deadline = deadline,
            linearId = this.linearId,
            attachmentId = attachmentId,
            dateOfCreation = this.dateOfCreation
    )

    fun withAttachment(attachmentId: SecureHash) = ToDoItem(
            assignedBy = this.assignedBy,
            assignedTo = this.assignedTo,
            task = this.task,
            deadline = this.deadline,
            linearId = this.linearId,
            attachmentId = attachmentId,
            dateOfCreation = this.dateOfCreation
    )
}