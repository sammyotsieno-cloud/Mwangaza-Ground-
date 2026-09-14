package core.domain.allocation

import core.domain.fefo.FefoCandidateAllocation
import core.domain.model.InventoryCostLayer
import core.domain.model.Money
import core.domain.model.Quantity
import core.domain.model.StockAllocation
import java.math.BigInteger
import java.util.UUID

/**
 * Result of allocating physical consumption across financial acquisition
 * cost layers.
 */
data class CostLayerAllocationResult(
    val allocations: List<StockAllocation>,
    val updatedCostLayers: List<InventoryCostLayer>,
    val totalCogs: Money
)

/**
 * Domain service responsible for allocating consumed physical quantities
 * across discrete InventoryCostLayer tranches and computing COGS.
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
 * - create or modify cost-layer identities.
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
 * Money is represented as integer minor currency units.
 *
 * Therefore, when acquisitionUnitCost is expressed per canonical quantity
 * unit, the exact monetary value represented by a quantity allocation is:
 *
 *     storageUnits × acquisitionUnitCost
 *     ---------------------------------
 *              10^scale
 *
 * The calculation MUST use exact integer arithmetic.
 *
 * No Double, Float, or implicit rounding is permitted.
 *
 * If the resulting monetary amount cannot be represented exactly in the
 * smallest currency unit, the allocation is rejected rather than silently
 * rounded.
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

        var totalCogsMinor = BigInteger.ZERO

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

                require(
                    layer.acquisitionUnitCost.amountMinorUnits >= 0L
                ) {
                    "Cost layer '${layer.id}' acquisition unit cost cannot " +
                        "be negative, got: " +
                        "${layer.acquisitionUnitCost.amountMinorUnits}"
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
                 * IMPORTANT:
                 *
                 * acquisitionUnitCost is per canonical quantity unit,
                 * while Quantity stores scaled integer storage units.
                 *
                 * Therefore we MUST divide by 10^scale.
                 *
                 * Example:
                 *
                 * scale = 2
                 * quantity = 1.50
                 * storageUnits = 150
                 * unit cost = 6,666 minor units
                 *
                 * exact mathematical cost:
                 *
                 *     150 × 6,666 / 100
                 *     = 9,999 minor units
                 *
                 * No raw-storage-unit multiplication is allowed.
                 * ---------------------------------------------------------
                 */
                val layerCogsMinor =
                    calculateExactAllocatedCostMinorUnits(
                        quantity = allocatedQuantity,
                        acquisitionUnitCost = layer.acquisitionUnitCost
                    )

                val allocatedCost =
                    Money(
                        layerCogsMinor
                            .longValueExact()
                    )

                /*
                 * ---------------------------------------------------------
                 * 8. Create the immutable financial bridge record.
                 *
                 * StockAllocation itself verifies that its quantity,
                 * historical acquisition unit cost, and allocated monetary
                 * value agree exactly.
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

                totalCogsMinor =
                    totalCogsMinor.add(layerCogsMinor)
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

        /*
         * -------------------------------------------------------------
         * 11. Ensure the aggregate COGS fits the Money representation.
         * -------------------------------------------------------------
         */
        val totalCogsMinorLong =
            totalCogsMinor.longValueExact()

        return CostLayerAllocationResult(
            allocations = allocations,
            updatedCostLayers = updatedLayers,
            totalCogs = Money(totalCogsMinorLong)
        )
    }

    /**
     * Calculates the exact monetary cost represented by a quantity at a
     * historical acquisition unit cost.
     *
     * Formula:
     *
     *     storageUnits × unitCostMinorUnits
     *     ---------------------------------
     *                10^scale
     *
     * The result MUST be an integer number of currency minor units.
     *
     * BigInteger is used so intermediate multiplication cannot overflow
     * Long before exactness is established.
     */
    private fun calculateExactAllocatedCostMinorUnits(
        quantity: Quantity,
        acquisitionUnitCost: Money
    ): BigInteger {

        val scaleFactor =
            BigInteger.TEN.pow(
                quantity.scale.scale
            )

        val numerator =
            BigInteger.valueOf(
                quantity.storageUnits
            ).multiply(
                BigInteger.valueOf(
                    acquisitionUnitCost.amountMinorUnits
                )
            )

        val quotientAndRemainder =
            numerator.divideAndRemainder(
                scaleFactor
            )

        require(
            quotientAndRemainder[1] == BigInteger.ZERO
        ) {
            "Exact COGS allocation is not representable in currency " +
                "minor units: quantity=$quantity, " +
                "acquisitionUnitCost=$acquisitionUnitCost"
        }

        require(
            quotientAndRemainder[0] >= BigInteger.ZERO
        ) {
            "Calculated COGS cannot be negative: " +
                "quantity=$quantity, " +
                "acquisitionUnitCost=$acquisitionUnitCost"
        }

        return quotientAndRemainder[0]
    }
}
