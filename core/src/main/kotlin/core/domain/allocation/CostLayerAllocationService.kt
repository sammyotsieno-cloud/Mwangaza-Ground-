package core.domain.allocation

import core.domain.fefo.FefoCandidateAllocation
import core.domain.model.InventoryCostLayer
import core.domain.model.Money
import core.domain.model.Quantity
import core.domain.model.StockAllocation
import java.util.UUID

/**
 * Result of allocating physical consumption across financial acquisition cost layers.
 */
data class CostLayerAllocationResult(
    val allocations: List<StockAllocation>,
    val updatedCostLayers: List<InventoryCostLayer>,
    val totalCogs: Money
)

/**
 * Domain service responsible for allocating consumed physical batch quantities
 * across discrete [InventoryCostLayer] tranches and computing exact Cost of Goods Sold (COGS).
 *
 * Separation of Concerns:
 * - FEFO selects the physical batches based on expiry and facility policy.
 * - This service allocates the consumption across the acquisition cost layers within the selected physical batches.
 * - Prioritizes earlier acquired cost layers (FIFO by acquiredAt -> createdAt -> id) within each physical batch.
 * - Calculates exact integer COGS without rounding or floating-point truncation.
 */
object CostLayerAllocationService {

    /**
     * Allocates the candidate batch quantities across authoritative cost layers.
     *
     * @param candidateAllocations The list of physical batch allocations determined by FEFO.
     * @param activeLayersByBatch Map of active cost layers available for each batch (where remainingQuantity > 0).
     * @param consumptionTransactionId Identifier of the parent [Sale] transaction.
     * @param consumptionItemId Identifier of the parent [SaleItem] line.
     * @param productId Canonical product ID.
     * @param allocationTimestamp Epoch millisecond timestamp of the transaction.
     * @return [CostLayerAllocationResult] containing the generated [StockAllocation]s, mutated [InventoryCostLayer]s, and total COGS.
     */
    fun allocateCostLayers(
        candidateAllocations: List<FefoCandidateAllocation>,
        activeLayersByBatch: Map<String, List<InventoryCostLayer>>,
        consumptionTransactionId: String,
        consumptionItemId: String,
        productId: String,
        allocationTimestamp: Long
    ): CostLayerAllocationResult {
        val allocations = mutableListOf<StockAllocation>()
        val updatedLayers = mutableListOf<InventoryCostLayer>()
        var totalCogsMinor = 0L

        for (candidate in candidateAllocations) {
            val batch = candidate.batch
            val neededQuantity = candidate.allocatedQuantity
            val availableLayers = activeLayersByBatch[batch.id] ?: emptyList()

            // Deterministic ordering: oldest acquisition first, then oldest registration, then entity ID
            val sortedLayers = availableLayers
                .filter { it.remainingQuantity.isPositive }
                .sortedWith(
                    compareBy<InventoryCostLayer> { it.acquiredAt }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                )

            val totalAvailableUnits = sortedLayers.sumOf { it.remainingQuantity.storageUnits }
            if (totalAvailableUnits < neededQuantity.storageUnits) {
                throw IllegalStateException(
                    "Insufficient cost layer balance for batch ${batch.batchNumber} (${batch.id}). " +
                        "Needed ${neededQuantity.storageUnits} units, but available cost layers have only $totalAvailableUnits units."
                )
            }

            var unitsRemainingToCover = neededQuantity.storageUnits

            for (layer in sortedLayers) {
                if (unitsRemainingToCover <= 0L) break

                require(layer.remainingQuantity.scale == neededQuantity.scale) {
                    "Cost layer scale (${layer.remainingQuantity.scale}) does not match consumption scale (${neededQuantity.scale})"
                }

                val availableInLayer = layer.remainingQuantity.storageUnits
                val unitsFromThisLayer = minOf(unitsRemainingToCover, availableInLayer)

                if (unitsFromThisLayer > 0L) {
                    val allocatedQty = Quantity(unitsFromThisLayer, neededQuantity.scale)
                    val layerCogsMinor = Math.multiplyExact(
                        unitsFromThisLayer,
                        layer.acquisitionUnitCost.amountMinorUnits
                    )
                    val allocatedCost = Money(layerCogsMinor)

                    val allocation = StockAllocation(
                        id = UUID.randomUUID().toString(),
                        consumptionTransactionId = consumptionTransactionId,
                        consumptionItemId = consumptionItemId,
                        productId = productId,
                        stockBatchId = batch.id,
                        inventoryCostLayerId = layer.id,
                        allocatedQuantity = allocatedQty,
                        acquisitionUnitCost = layer.acquisitionUnitCost,
                        allocatedCost = allocatedCost,
                        allocatedAt = allocationTimestamp,
                        createdAt = allocationTimestamp
                    )
                    allocations.add(allocation)

                    val newRemainingQty = layer.remainingQuantity - allocatedQty
                    val updatedLayer = layer.copy(
                        remainingQuantity = newRemainingQty,
                        updatedAt = maxOf(allocationTimestamp, layer.updatedAt)
                    )
                    updatedLayers.add(updatedLayer)

                    unitsRemainingToCover -= unitsFromThisLayer
                    totalCogsMinor = Math.addExact(totalCogsMinor, layerCogsMinor)
                }
            }

            if (unitsRemainingToCover > 0L) {
                throw IllegalStateException(
                    "Failed to fully allocate cost layers for batch ${batch.batchNumber}: " +
                        "$unitsRemainingToCover units remained unallocated."
                )
            }
        }

        return CostLayerAllocationResult(
            allocations = allocations,
            updatedCostLayers = updatedLayers,
            totalCogs = Money(totalCogsMinor)
        )
    }
}
