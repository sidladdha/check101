@file:JvmName("ClauseVerifier")

package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionForContract

/**
 * Verify a transaction against the given list of clauses.
 *
 * @param tx transaction to be verified.
 * @param clauses the clauses to verify.
 * @param commands commands extracted from the transaction, which are relevant to the
 * clauses.
 */
fun <C : CommandData> verifyClause(tx: TransactionForContract,
                                   clause: Clause<ContractState, C, Unit>,
                                   commands: List<AuthenticatedObject<C>>) {
    if (Clause.log.isTraceEnabled) {
        clause.getExecutionPath(commands).forEach {
            Clause.log.trace("Tx ${tx.origHash} clause: $clause")
        }
    }
    val matchedCommands = clause.verify(tx, tx.inputs, tx.outputs, commands, null)

    check(matchedCommands.containsAll(commands.map { it.value })) { "The following commands were not matched at the end of execution: " + (commands - matchedCommands) }
}
