package core.domain.persistence

/**
 * Abstraction for executing operations within an atomic transactional boundary.
 *
 * Enables running identical transaction isolation logic in production (via Room / SQLite)
 * and in deterministic unit testing environments.
 */
interface TransactionRunner {
    fun <T> runInTransaction(block: () -> T): T
}

/**
 * Production implementation of [TransactionRunner] delegating to [CoreDatabase.runInTransaction].
 */
class RoomTransactionRunner(
    private val database: CoreDatabase
) : TransactionRunner {
    override fun <T> runInTransaction(block: () -> T): T {
        var result: T? = null
        database.runInTransaction {
            result = block()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
