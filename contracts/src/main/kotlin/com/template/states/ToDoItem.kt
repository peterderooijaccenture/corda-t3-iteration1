package com.template.states

import com.template.contracts.ToDoItemContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import java.util.*

@BelongsToContract(ToDoItemContract::class)
class ToDoItem(
        val assignedBy: Party,
        val assignedTo: Party,
        val taskDescription: String,
        val status: Status = Status.OPEN,
        val deadLine: LocalDate? = null,
        override val linearId: UniqueIdentifier = UniqueIdentifier(id = UUID.randomUUID())
) : LinearState {
    val dateOfCreation: LocalDate = LocalDate.now()

    @CordaSerializable
    enum class Status { OPEN, CLOSED }

    /**
     * A _participant_ is any party that should be notified when the state is created or consumed.
     *
     * The list of participants is required for certain types of transactions. For example, when changing the notary
     * for this state, every participant has to be involved and approve the transaction
     * so that they receive the updated state, and don't end up in a situation where they can no longer use a state
     * they possess, since someone consumed that state during the notary change process.
     *
     * The participants list should normally be derived from the contents of the state.
     */
    override val participants: List<AbstractParty>
        get() = listOf(assignedTo, assignedBy)

    override fun toString() = "ToDoItem $linearId: [$status] \"$taskDescription\" assigned to $assignedTo by $assignedBy"
}