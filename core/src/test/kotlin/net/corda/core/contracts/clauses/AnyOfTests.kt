package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TransactionForContract
import net.corda.core.crypto.SecureHash
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnyOfTests {
    @Test
    fun minimal() {
        val counter = AtomicInteger(0)
        val clause = AnyOf(matchedClause(counter), matchedClause(counter))
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        verifyClause(tx, clause, emptyList<AuthenticatedObject<CommandData>>())

        // Check that we've run the verify() function of two clauses
        assertEquals(2, counter.get())
    }

    @Test
    fun `not all match`() {
        val counter = AtomicInteger(0)
        val clause = AnyOf(matchedClause(counter), unmatchedClause(counter))
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        verifyClause(tx, clause, emptyList<AuthenticatedObject<CommandData>>())

        // Check that we've run the verify() function of one clause
        assertEquals(1, counter.get())
    }

    @Test
    fun `none match`() {
        val counter = AtomicInteger(0)
        val clause = AnyOf(unmatchedClause(counter), unmatchedClause(counter))
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        assertFailsWith(IllegalArgumentException::class) {
            verifyClause(tx, clause, emptyList<AuthenticatedObject<CommandData>>())
        }
    }
}
