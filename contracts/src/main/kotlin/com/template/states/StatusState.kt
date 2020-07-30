package com.template.states

import com.template.contracts.StatusStateContract
import com.template.contracts.ToDoItemContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import java.util.*

@BelongsToContract(StatusStateContract::class)
class StatusState(
        val open: Boolean,
        override val participants: List<AbstractParty>,
        override val linearId: UniqueIdentifier = UniqueIdentifier(id = UUID.randomUUID())) : LinearState