package core.domain.fefo

import core.domain.model.Quantity
import core.domain.model.StockBatch
import core.domain.time.LocalDateValue

/**
 * Evaluated candidate batch representation for FEFO selection.
 */
data class FefoBatchCandidate(
    val batch: StockBatch,
    val availableQuantity: Quantity,
    val expiryStatus: ExpiryStatus,
    val isEligible: Boolean
)

/**
 * Specific batch candidate allocation within a multi-batch FEFO plan.
 */
data class FefoCandidateAllocation(
    val batch: StockBatch,
    val allocatedQuantity: Quantity
)

/**
 * Result of planning consumption of a requested quantity across candidate batches under FEFO.
 */
data class FefoAllocationPlan(
    val requestedQuantity: Quantity,
    val allocations: List<FefoCandidateAllocation>,
    val allocatedTotal: Quantity,
    val unfulfilledQuantity: Quantity,
    val isFullySatisfied: Boolean
)

/**
 * Pure domain service for evaluating batch expiry and selecting stock candidates under
 * First-Expired, First-Out (FEFO) rules.
 *
 * Strict Architectural Boundaries:
 * - FEFO is a SELECTION POLICY, not an inventory mutation service.
 * - This service does NOT deduct stock, does NOT mutate the database, does NOT create sales,
 *   does NOT append [StockMovement]s, and does NOT allocate cost layers / COGS.
 * - Cost layer allocation belongs to future StockAllocation.
 * - Deterministic Ordering: Tie-breaking is strictly defined to prevent non-deterministic
 *   database row order from altering candidate selection.
 * - Multi-Batch Preparedness: Seamlessly allocates across multiple batches (e.g. 100 from Batch A,
 *   20 from Batch B for a 120-unit request) without executing premature sales.
 */
object FefoService {

    /**
     * Evaluates a collection of physical batches with their currently available physical balances
     * against a facility calendar date and operational policy.
     */
    fun evaluateCandidates(
        batchesWithQuantities: List<Pair<StockBatch, Quantity>>,
        facilityCalendarDate: LocalDateValue,
        policy: ExpiryPolicy = ExpiryPolicy.DEFAULT
    ): List<FefoBatchCandidate> {
        return batchesWithQuantities.map { (batch, availableQuantity) ->
            val status = policy.evaluate(batch.expiryDateInt, facilityCalendarDate)
            val isEligible = policy.isEligibleForDispensing(status) && availableQuantity.isPositive
            FefoBatchCandidate(
                batch = batch,
                availableQuantity = availableQuantity,
                expiryStatus = status,
                isEligible = isEligible
            )
        }
    }

    /**
     * Orders candidate batches under strict, deterministic FEFO sequencing:
     * 1. Only eligible candidates with strictly positive available quantity participate.
     * 2. Batches with known expiry dates (expiryDateInt != -1) are prioritized before unknown/non-expiring batches.
     * 3. Known-expiry batches are ordered by [StockBatch.expiryDateInt] ASCENDING (earliest expiry first).
     * 4. Deterministic tie-breaking for identical expiry dates:
     *    a. [StockBatch.createdAt] ASCENDING (FIFO among same-expiry batches)
     *    b. [StockBatch.batchNumber] ASCENDING
     *    c. [StockBatch.id] ASCENDING
     * 5. Unknown/non-expiring batches are ordered:
     *    a. [StockBatch.createdAt] ASCENDING
     *    b. [StockBatch.batchNumber] ASCENDING
     *    c. [StockBatch.id] ASCENDING
     */
    fun sortFefoOrder(candidates: List<FefoBatchCandidate>): List<FefoBatchCandidate> {
        return candidates
            .filter { it.isEligible && it.availableQuantity.isPositive }
            .sortedWith(
                Comparator { a, b ->
                    val aExpiry = a.batch.expiryDateInt
                    val bExpiry = b.batch.expiryDateInt
                    val aHasExpiry = aExpiry != StockBatch.EXPIRY_UNKNOWN_OR_NONE
                    val bHasExpiry = bExpiry != StockBatch.EXPIRY_UNKNOWN_OR_NONE

                    // Expiry-bearing batches come before non-expiring/unknown batches
                    if (aHasExpiry && !bHasExpiry) return@Comparator -1
                    if (!aHasExpiry && bHasExpiry) return@Comparator 1

                    if (aHasExpiry && bHasExpiry) {
                        val expiryDiff = aExpiry.compareTo(bExpiry)
                        if (expiryDiff != 0) return@Comparator expiryDiff
                    }

                    // Tie-breaker 1: Registration instant (FIFO)
                    val createdDiff = a.batch.createdAt.compareTo(b.batch.createdAt)
                    if (createdDiff != 0) return@Comparator createdDiff

                    // Tie-breaker 2: Batch number lexical order
                    val batchNumberDiff = a.batch.batchNumber.compareTo(b.batch.batchNumber)
                    if (batchNumberDiff != 0) return@Comparator batchNumberDiff

                    // Tie-breaker 3: Entity primary key ID
                    a.batch.id.compareTo(b.batch.id)
                }
            )
    }

    /**
     * Computes a multi-batch candidate allocation plan to fulfill a [requestedQuantity] under FEFO.
     *
     * Pure function: Does NOT alter stock balances or create database records.
     *
     * @param requestedQuantity Quantity requested for consumption (must be strictly positive)
     * @param evaluatedCandidates List of pre-evaluated batch candidates
     * @return [FefoAllocationPlan] detailing batch-by-batch candidate allocations
     */
    fun planAllocation(
        requestedQuantity: Quantity,
        evaluatedCandidates: List<FefoBatchCandidate>
    ): FefoAllocationPlan {
        require(requestedQuantity.isPositive) {
            "Requested quantity must be strictly positive: ${requestedQuantity.storageUnits}"
        }

        val sorted = sortFefoOrder(evaluatedCandidates)
        val allocations = mutableListOf<FefoCandidateAllocation>()

        var remainingToAllocate = requestedQuantity.storageUnits
        var allocatedTotalUnits = 0L

        for (candidate in sorted) {
            if (remainingToAllocate <= 0L) break

            require(candidate.availableQuantity.scale == requestedQuantity.scale) {
                "Candidate batch scale (${candidate.availableQuantity.scale}) does not match requested scale (${requestedQuantity.scale})"
            }

            val availableUnits = candidate.availableQuantity.storageUnits
            val unitsFromThisBatch = minOf(remainingToAllocate, availableUnits)

            if (unitsFromThisBatch > 0L) {
                allocations.add(
                    FefoCandidateAllocation(
                        batch = candidate.batch,
                        allocatedQuantity = Quantity(unitsFromThisBatch, requestedQuantity.scale)
                    )
                )
                remainingToAllocate -= unitsFromThisBatch
                allocatedTotalUnits += unitsFromThisBatch
            }
        }

        val unfulfilledUnits = remainingToAllocate
        val allocatedTotal = Quantity(allocatedTotalUnits, requestedQuantity.scale)
        val unfulfilledQuantity = Quantity(unfulfilledUnits, requestedQuantity.scale)

        return FefoAllocationPlan(
            requestedQuantity = requestedQuantity,
            allocations = allocations,
            allocatedTotal = allocatedTotal,
            unfulfilledQuantity = unfulfilledQuantity,
            isFullySatisfied = unfulfilledUnits == 0L
        )
    }
}
