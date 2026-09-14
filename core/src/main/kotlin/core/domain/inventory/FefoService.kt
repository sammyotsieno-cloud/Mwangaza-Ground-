package core.domain.inventory

import core.domain.model.ExpiryPolicy
import core.domain.model.Quantity
import core.domain.model.StockBatch
import core.domain.time.LocalDateValue

/**
 * Plans First-Expiry-First-Out (FEFO) selection of physical stock.
 *
 * Architectural authority:
 *
 * - StockBatch answers:
 *     "Which physical lot is this stock from?"
 *
 * - ExpiryPolicy answers:
 *     "Is this batch eligible for stock exit today?"
 *
 * - FefoService answers:
 *     "Given eligible batches and their already-authoritative available
 *      quantities, which physical batches should satisfy this requested
 *      quantity first?"
 *
 * - StockMovement remains the authority for physical stock-flow events.
 *
 * - InventoryCostLayer remains the authority for acquisition-cost pools.
 *
 * - StockAllocation remains the bridge between a consumption line and
 *   acquisition-cost layers.
 *
 * FefoService therefore performs SELECTION PLANNING only.
 *
 * ---------------------------------------------------------------------------
 * RESPONSIBILITIES
 * ---------------------------------------------------------------------------
 *
 * FefoService:
 *
 * - validates candidate quantity compatibility;
 * - excludes expired stock;
 * - excludes unknown-expiry stock;
 * - permits explicitly non-expiring stock;
 * - orders eligible batches deterministically by expiry;
 * - selects quantities until the requested quantity is satisfied;
 * - reports any quantity that could not be allocated from the candidates.
 *
 * FefoService MUST NOT:
 *
 * - query the database;
 * - calculate stock balances;
 * - mutate StockBatch;
 * - mutate InventoryCostLayer;
 * - decrement inventory;
 * - create StockMovement records;
 * - create StockAllocation records;
 * - calculate COGS;
 * - calculate selling prices;
 * - create or commit sales;
 * - silently consume unknown-expiry stock;
 * - silently convert quantity scales.
 *
 * ---------------------------------------------------------------------------
 * AVAILABLE QUANTITY
 * ---------------------------------------------------------------------------
 *
 * Candidate.availableQuantity is supplied by the inventory workflow.
 *
 * FefoService deliberately does not determine where that quantity came from.
 *
 * The caller is responsible for supplying an authoritative physical
 * availability snapshot.
 *
 * This prevents FEFO from becoming a second inventory ledger.
 *
 * ---------------------------------------------------------------------------
 * QUANTITY SEMANTICS
 * ---------------------------------------------------------------------------
 *
 * All candidate quantities must use the same QuantityScale as the requested
 * quantity.
 *
 * Quantity deliberately rejects arithmetic and comparison between different
 * scales, so FEFO fails explicitly rather than performing an implicit
 * rescaling.
 *
 * ---------------------------------------------------------------------------
 * FEFO ORDER
 * ---------------------------------------------------------------------------
 *
 * Eligible known-expiry batches are ordered by earliest expiry first.
 *
 * If two batches have the same expiry date, deterministic tie-breakers are:
 *
 *     1. productId
 *     2. batchNumber
 *     3. expiryDateInt
 *     4. StockBatch.id
 *
 * Explicitly non-expiring stock is placed after all known-expiry stock.
 *
 * Unknown-expiry stock is excluded by ExpiryPolicy and is therefore never
 * silently selected.
 *
 * Expired stock is likewise excluded.
 */
object FefoService {

    /**
     * A physical batch together with the quantity that the caller has already
     * determined to be available for stock exit.
     */
    data class Candidate(
        val batch: StockBatch,
        val availableQuantity: Quantity
    ) {
        init {
            require(availableQuantity.isPositive) {
                "FEFO candidate available quantity must be strictly positive " +
                    "(batchId=${batch.id}, quantity=$availableQuantity)"
            }
        }
    }

    /**
     * A physical selection made by the FEFO planning algorithm.
     *
     * quantity is always positive.
     *
     * This is a plan, not a stock mutation.
     */
    data class Allocation(
        val batch: StockBatch,
        val quantity: Quantity
    ) {
        init {
            require(quantity.isPositive) {
                "FEFO allocation quantity must be strictly positive " +
                    "(batchId=${batch.id}, quantity=$quantity)"
            }
        }
    }

    /**
     * Complete result of a FEFO planning operation.
     *
     * requestedQuantity =
     *     quantity the consuming workflow requested.
     *
     * allocatedQuantity =
     *     quantity that FEFO could assign from eligible candidates.
     *
     * remainingQuantity =
     *     quantity still unsatisfied by the supplied candidates.
     *
     * No inventory mutation occurs when a Plan is created.
     */
    data class Plan(
        val requestedQuantity: Quantity,
        val allocations: List<Allocation>,
        val allocatedQuantity: Quantity,
        val remainingQuantity: Quantity
    ) {

        init {
            require(requestedQuantity.isPositive) {
                "Requested FEFO quantity must be strictly positive"
            }

            require(allocatedQuantity.storageUnits >= 0L) {
                "Allocated FEFO quantity cannot be negative"
            }

            require(remainingQuantity.storageUnits >= 0L) {
                "Remaining FEFO quantity cannot be negative"
            }

            require(
                allocatedQuantity.scale == requestedQuantity.scale
            ) {
                "Allocated FEFO quantity must use requested quantity scale"
            }

            require(
                remainingQuantity.scale == requestedQuantity.scale
            ) {
                "Remaining FEFO quantity must use requested quantity scale"
            }

            require(
                allocatedQuantity + remainingQuantity == requestedQuantity
            ) {
                "FEFO plan quantity reconciliation failed: " +
                    "requested=$requestedQuantity, " +
                    "allocated=$allocatedQuantity, " +
                    "remaining=$remainingQuantity"
            }

            val allocationQuantity =
                allocations.fold(
                    Quantity.zero(requestedQuantity.scale)
                ) { total, allocation ->
                    total + allocation.quantity
                }

            require(allocationQuantity == allocatedQuantity) {
                "FEFO allocation list does not reconcile with allocatedQuantity: " +
                    "listQuantity=$allocationQuantity, " +
                    "allocatedQuantity=$allocatedQuantity"
            }

            require(
                allocations.map { it.batch.id }.distinct().size ==
                    allocations.size
            ) {
                "FEFO plan must not allocate the same StockBatch more than once"
            }
        }

        val isFullyAllocated: Boolean
            get() = remainingQuantity.isZero

        val isPartiallyAllocated: Boolean
            get() =
                allocatedQuantity.isPositive &&
                    remainingQuantity.isPositive

        val isUnallocated: Boolean
            get() = allocatedQuantity.isZero
    }

    /**
     * Plans FEFO selection from the supplied physical-stock candidates.
     *
     * No persistence or inventory mutation occurs.
     */
    fun plan(
        requestedQuantity: Quantity,
        candidates: List<Candidate>,
        today: LocalDateValue
    ): Plan {

        require(requestedQuantity.isPositive) {
            "Requested FEFO quantity must be strictly positive"
        }

        requireUniqueBatchIds(candidates)

        requireCompatibleScales(
            requestedQuantity = requestedQuantity,
            candidates = candidates
        )

        val eligibleCandidates =
            candidates
                .filter { candidate ->
                    ExpiryPolicy.isEligibleForStockExit(
                        batch = candidate.batch,
                        today = today
                    )
                }
                .sortedWith(
                    compareBy<Candidate>(
                        { expiryOrder(it.batch, today) },
                        { it.batch.productId },
                        { it.batch.batchNumber },
                        { it.batch.expiryDateInt },
                        { it.batch.id }
                    )
                )

        var remaining = requestedQuantity

        val allocations =
            mutableListOf<Allocation>()

        for (candidate in eligibleCandidates) {

            if (remaining.isZero) {
                break
            }

            val selectedQuantity =
                if (candidate.availableQuantity <= remaining) {
                    candidate.availableQuantity
                } else {
                    remaining
                }

            if (!selectedQuantity.isPositive) {
                continue
            }

            allocations += Allocation(
                batch = candidate.batch,
                quantity = selectedQuantity
            )

            remaining -= selectedQuantity
        }

        val allocated =
            requestedQuantity - remaining

        return Plan(
            requestedQuantity = requestedQuantity,
            allocations = allocations.toList(),
            allocatedQuantity = allocated,
            remainingQuantity = remaining
        )
    }

    private fun requireUniqueBatchIds(
        candidates: List<Candidate>
    ) {
        val duplicateBatchIds =
            candidates
                .groupingBy { it.batch.id }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys

        require(duplicateBatchIds.isEmpty()) {
            "FEFO candidates contain duplicate StockBatch ids: " +
                duplicateBatchIds.sorted()
        }
    }

    private fun requireCompatibleScales(
        requestedQuantity: Quantity,
        candidates: List<Candidate>
    ) {
        val incompatible =
            candidates.filter {
                it.availableQuantity.scale !=
                    requestedQuantity.scale
            }

        require(incompatible.isEmpty()) {
            val batchIds =
                incompatible.map { it.batch.id }

            "FEFO candidates contain quantities with scales incompatible " +
                "with requested quantity scale ${requestedQuantity.scale}: " +
                batchIds
        }
    }

    /**
     * Produces the deterministic primary FEFO ordering value.
     *
     * Known expiry dates use their YYYYMMDD value, so earlier dates sort first.
     *
     * Non-expiring stock is deliberately placed after all known-expiry stock.
     *
     * Expired and unknown-expiry batches should already have been removed by
     * ExpiryPolicy. Reaching either state here therefore indicates a broken
     * policy boundary rather than a normal selection case.
     */
    private fun expiryOrder(
        batch: StockBatch,
        today: LocalDateValue
    ): Int {

        return when (
            ExpiryPolicy.status(
                batch = batch,
                today = today
            )
        ) {

            ExpiryPolicy.Status.VALID,
            ExpiryPolicy.Status.EXPIRING_TODAY ->
                batch.expiryDateInt

            ExpiryPolicy.Status.NON_EXPIRING ->
                NON_EXPIRING_ORDER

            ExpiryPolicy.Status.EXPIRED ->
                error(
                    "Expired StockBatch reached FEFO ordering despite " +
                        "ExpiryPolicy exclusion: batchId=${batch.id}"
                )

            ExpiryPolicy.Status.UNKNOWN ->
                error(
                    "Unknown-expiry StockBatch reached FEFO ordering despite " +
                        "ExpiryPolicy exclusion: batchId=${batch.id}"
                )
        }
    }

    private const val NON_EXPIRING_ORDER: Int =
        Int.MAX_VALUE
}
