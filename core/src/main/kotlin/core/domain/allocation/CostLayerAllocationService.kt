package core.domain.allocation

import core.domain.fefo.FefoCandidateAllocation
import core.domain.model.InventoryCostLayer
import core.domain.model.Quantity
import core.domain.model.RationalCost
import core.domain.model.StockAllocation
import java.math.BigInteger
import java.util.UUID

/**
 * Result of allocating physical consumption across financial acquisition
 * cost layers.
 *
 * totalCogs is retained as an exact RationalCost.
 *
 * It MUST NOT be rounded merely because the value is later displayed as
 * currency with two decimal places.
 */
data class CostLayerAllocationResult(
    val allocations: List<StockAllocation>,
    val updatedCostLayers: List<InventoryCostLayer>,
    val totalCogs: RationalCost
)

/**
 * Domain service responsible for allocating consumed physical quantities
 * across discrete InventoryCostLayer balances and computing exact COGS.
 *
 * RESPONSIBILITY
 * --------------
 * FEFO determines which physical batches should supply the requested
 * quantity. This service determines which acquisition-cost layers within
 * those selected batches explain the cost of that physical consumption.
 *
 * This service does NOT:
 * - select stock by expiry;
 * - create physical stock movements;
 * - maintain a second inventory ledger;
 * - calculate selling prices;
 * - rewrite historical acquisition costs;
 * - create or modify cost-layer identities;
 * - round authoritative financial values for display.
 *
 * FEFO selects physical stock.
 * InventoryCostLayer represents historical acquisition-cost pools.
 * StockAllocation records the financial bridge from consumed quantity to
 * acquisition cost.
 *
 * COST / QUANTITY MODEL
 * ---------------------
 * Quantity is represented as:
 *
 *     physical quantity =
 *         storageUnits / 10^scale
 *
 * acquisitionUnitCost is an exact RationalCost per canonical quantity unit.
 *
 * Therefore the exact monetary value represented by a quantity allocation is:
 *
 *     allocatedCost =
 *         acquisitionUnitCost
 *             × storageUnits
 *             × 1 / 10^scale
 *
 * Equivalently:
 *
 *     allocatedCost =
 *         acquisitionUnitCost ×
 *             (storageUnits / 10^scale)
 *
 * All authoritative calculations remain rational and exact.
 *
 * No Double, Float, integer monetary division, truncation, or display
 * rounding is permitted here.
 *
 * Display rounding belongs at the presentation boundary.
 */
object CostLayerAllocationService {

    /**
     * Allocates the candidate batch quantities across authoritative cost
     * layers.
     *
     * @param candidateAllocations physical batch quantities already selected
     *        by FEFO.
     * @param activeLayersByBatch active acquisition-cost layers for each
     *        selected physical batch.
     * @param consumptionTransactionId identifier of the parent Sale.
     * @param consumptionItemId identifier of the parent SaleItem.
     * @param productId canonical product identity.
     * @param allocationTimestamp epoch-millisecond allocation timestamp.
     *
     * @return generated StockAllocation records, updated cost layers, and
     *         exact total COGS.
     */
    fun allocateCostLayers(
        candidateAllocations: List<FefoCandidateAllocation>,
        activeLayersByBatch: Map<String, List<InventoryCostLayer>>,
        consumptionTransactionId: String,
        consumptionItemId: String,
        productId: String,
        allocationTimestamp: Long
    ): CostLayerAllocationResult {

        require(consumptionTransactionId.isNotBlank()) {
            "consumptionTransactionId must not be blank"
        }

        require(consumptionItemId.isNotBlank()) {
            "consumptionItemId must not be blank"
        }

        require(productId.isNotBlank()) {
            "productId must not be blank"
        }

        require(allocationTimestamp > 0L) {
            "allocationTimestamp must be a positive epoch timestamp, " +
                "got: $allocationTimestamp"
        }

        val allocations = mutableListOf<StockAllocation>()
        val updatedLayers = mutableListOf<InventoryCostLayer>()

        var totalCogs =
            RationalCost(
                numerator = BigInteger.ZERO,
                denominator = BigInteger.ONE
            )

        for (candidate in candidateAllocations) {

            val batch = candidate.batch
            val neededQuantity = candidate.allocatedQuantity

            /*
             * -------------------------------------------------------------
             * 1. Validate the requested physical allocation.
             * -------------------------------------------------------------
             */
            require(neededQuantity.isPositive) {
                "Candidate allocated quantity must be strictly positive " +
                    "(> 0), got: ${neededQuantity.storageUnits} " +
                    "(batch=${batch.id})"
            }

            require(batch.productId == productId) {
                "Product/Batch relationship violation: Batch '${batch.id}' " +
                    "belongs to product '${batch.productId}', but expected " +
                    "product '$productId'"
            }

            /*
             * -------------------------------------------------------------
             * 2. Resolve the cost layers belonging to this physical batch.
             * -------------------------------------------------------------
             */
            val availableLayers =
                activeLayersByBatch[batch.id] ?: emptyList()

            /*
             * -------------------------------------------------------------
             * 3. Validate every layer before allocating anything from it.
             *
             * This prevents one malformed layer from being partially
             * consumed before its invariant failure is discovered.
             * -------------------------------------------------------------
             */
            for (layer in availableLayers) {

                require(layer.productId == productId) {
                    "Product/Layer relationship violation: Cost layer " +
                        "'${layer.id}' belongs to product " +
                        "'${layer.productId}', expected '$productId'"
                }

                require(layer.stockBatchId == batch.id) {
                    "Batch/Layer relationship violation: Cost layer " +
                        "'${layer.id}' belongs to batch " +
                        "'${layer.stockBatchId}', expected '${batch.id}'"
                }

                require(
                    layer.remainingQuantity.scale ==
                        neededQuantity.scale
                ) {
                    "Quantity scale mismatch: Cost layer '${layer.id}' " +
                        "scale (${layer.remainingQuantity.scale}) does not " +
                        "match consumption scale " +
                        "(${neededQuantity.scale})"
                }

                require(
                    layer.remainingQuantity.storageUnits >= 0L
                ) {
                    "Cost layer '${layer.id}' remaining quantity cannot " +
                        "be negative, got: " +
                        "${layer.remainingQuantity.storageUnits}"
                }

                require(
                    layer.remainingQuantity.storageUnits <=
                        layer.initialQuantity.storageUnits
                ) {
                    "Cost layer '${layer.id}' remaining quantity " +
                        "(${layer.remainingQuantity.storageUnits}) exceeds " +
                        "initial quantity " +
                        "(${layer.initialQuantity.storageUnits})"
                }

                require(layer.acquisitionUnitCost.isNonNegative) {
                    "Cost layer '${layer.id}' acquisition unit cost cannot " +
                        "be negative, got: " +
                        "${layer.acquisitionUnitCost}"
                }
            }

            /*
             * -------------------------------------------------------------
             * 4. Deterministic acquisition-cost ordering.
             *
             * Earlier acquisition layers are consumed first within the
             * physical batch.
             * -------------------------------------------------------------
             */
            val sortedLayers = availableLayers
                .filter { it.remainingQuantity.isPositive }
                .sortedWith(
                    compareBy<InventoryCostLayer> { it.acquiredAt }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                )

            /*
             * -------------------------------------------------------------
             * 5. Verify sufficient cost-layer quantity exists.
             *
             * All quantities have already been verified to use the same
             * scale as the requested consumption.
             * -------------------------------------------------------------
             */
            val totalAvailableUnits = sortedLayers.sumOf {
                it.remainingQuantity.storageUnits
            }

            require(
                totalAvailableUnits >= neededQuantity.storageUnits
            ) {
                "Insufficient cost layer balance for batch " +
                    "${batch.batchNumber} (${batch.id}). " +
                    "Needed ${neededQuantity.storageUnits} storage units, " +
                    "but available cost layers have only " +
                    "$totalAvailableUnits storage units."
            }

            var unitsRemainingToCover =
                neededQuantity.storageUnits

            /*
             * -------------------------------------------------------------
             * 6. Consume the selected cost layers in deterministic order.
             * -------------------------------------------------------------
             */
            for (layer in sortedLayers) {

                if (unitsRemainingToCover <= 0L) {
                    break
                }

                val availableInLayer =
                    layer.remainingQuantity.storageUnits

                val unitsFromThisLayer =
                    minOf(
                        unitsRemainingToCover,
                        availableInLayer
                    )

                require(unitsFromThisLayer > 0L) {
                    "Internal allocation error: selected layer " +
                        "'${layer.id}' produced a non-positive allocation."
                }

                require(unitsFromThisLayer <= availableInLayer) {
                    "Invariant violation: Attempting to allocate " +
                        "$unitsFromThisLayer storage units exceeding " +
                        "available $availableInLayer in layer '${layer.id}'"
                }

                val allocatedQuantity =
                    Quantity(
                        storageUnits = unitsFromThisLayer,
                        scale = neededQuantity.scale
                    )

                /*
                 * ---------------------------------------------------------
                 * 7. Calculate exact COGS.
                 *
                 * acquisitionUnitCost is an exact RationalCost per
                 * canonical quantity unit.
                 *
                 * allocatedQuantity represents:
                 *
                 *     storageUnits / 10^scale
                 *
                 * Therefore:
                 *
                 *     allocatedCost =
                 *         acquisitionUnitCost ×
                 *             storageUnits / 10^scale
                 *
                 * The result remains RationalCost.
                 *
                 * There is deliberately NO conversion to Money here.
                 * There is deliberately NO requirement that the result
                 * be an integral number of currency minor units.
                 * ---------------------------------------------------------
                 */
                val allocatedCost =
                    calculateExactAllocatedCost(
                        quantity = allocatedQuantity,
                        acquisitionUnitCost =
                            layer.acquisitionUnitCost
                    )

                require(allocatedCost.isNonNegative) {
                    "Calculated allocation cost cannot be negative: " +
                        "layer=${layer.id}, " +
                        "quantity=$allocatedQuantity, " +
                        "unitCost=${layer.acquisitionUnitCost}"
                }

                /*
                 * ---------------------------------------------------------
                 * 8. Create the immutable financial bridge record.
                 *
                 * StockAllocation verifies that:
                 *
                 *     allocatedCost =
                 *         acquisitionUnitCost × allocatedQuantity
                 *
                 * exactly.
                 * ---------------------------------------------------------
                 */
                val allocation =
                    StockAllocation(
                        id = UUID.randomUUID().toString(),
                        consumptionTransactionId =
                            consumptionTransactionId,
                        consumptionItemId =
                            consumptionItemId,
                        productId = productId,
                        stockBatchId = batch.id,
                        inventoryCostLayerId = layer.id,
                        allocatedQuantity = allocatedQuantity,
                        acquisitionUnitCost =
                            layer.acquisitionUnitCost,
                        allocatedCost = allocatedCost,
                        allocatedAt = allocationTimestamp,
                        createdAt = allocationTimestamp
                    )

                allocations.add(allocation)

                /*
                 * ---------------------------------------------------------
                 * 9. Reduce only the operational quantity state of the
                 *    cost layer.
                 *
                 * Historical acquisition facts remain unchanged.
                 * ---------------------------------------------------------
                 */
                val newRemainingQuantity =
                    layer.remainingQuantity - allocatedQuantity

                val updatedLayer =
                    layer.copy(
                        remainingQuantity = newRemainingQuantity,
                        updatedAt =
                            maxOf(
                                allocationTimestamp,
                                layer.updatedAt
                            )
                    )

                updatedLayers.add(updatedLayer)

                unitsRemainingToCover -= unitsFromThisLayer

                /*
                 * Exact aggregate COGS.
                 *
                 * No display rounding occurs here.
                 */
                totalCogs =
                    totalCogs.add(allocatedCost)
            }

            /*
             * -------------------------------------------------------------
             * 10. The candidate allocation must be completely covered.
             * -------------------------------------------------------------
             */
            require(unitsRemainingToCover == 0L) {
                "Failed to fully allocate cost layers for batch " +
                    "${batch.batchNumber}: " +
                    "$unitsRemainingToCover storage units remained " +
                    "unallocated."
            }
        }

        return CostLayerAllocationResult(
            allocations = allocations,
            updatedCostLayers = updatedLayers,
            totalCogs = totalCogs
        )
    }

    /**
     * Calculates the exact monetary cost represented by a quantity at a
     * historical acquisition unit cost.
     *
     * Formula:
     *
     *     allocatedCost =
     *         acquisitionUnitCost ×
     *             storageUnits / 10^scale
     *
     * RationalCost performs the multiplication and division without
     * converting the result to integer currency minor units.
     *
     * Example:
     *
     *     acquisitionUnitCost = 100/3
     *     quantity            = 1
     *
     *     allocatedCost       = 100/3
     *
     * Another example:
     *
     *     acquisitionUnitCost = 100/3
     *     quantity            = 3
     *
     *     allocatedCost       = 100
     *
     * The mathematical value is retained exactly in both cases.
     */
    private fun calculateExactAllocatedCost(
        quantity: Quantity,
        acquisitionUnitCost: RationalCost
    ): RationalCost {

        val scaleFactor =
            BigInteger.TEN.pow(
                quantity.scale.scale
            )

        return acquisitionUnitCost.multiply(
            numerator = BigInteger.valueOf(
                quantity.storageUnits
            ),
            denominator = scaleFactor
        )
    }
}
