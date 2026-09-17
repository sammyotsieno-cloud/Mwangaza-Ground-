package core.domain.reporting

import core.domain.model.InventoryCostLayer
import core.domain.model.RationalCost
import core.domain.persistence.InventoryCostLayerDao
import java.math.BigInteger

/**
 * Calculates the exact current financial value of inventory represented by
 * active InventoryCostLayer records.
 *
 * This service is the domain authority for current inventory valuation.
 *
 * It deliberately does not:
 *
 * - round monetary values;
 * - convert RationalCost to Money;
 * - format values for display;
 * - calculate sales revenue;
 * - calculate COGS;
 * - determine FEFO order;
 * - mutate stock;
 * - modify cost layers;
 * - apply selling prices.
 *
 * The authoritative valuation formula for one active cost layer is:
 *
 *     acquisitionUnitCost × remainingPhysicalQuantity
 *
 * acquisitionUnitCost is expressed per physical unit under the receiving
 * contract. remainingQuantity stores that physical quantity as integer
 * storage units, so valuation must divide by the quantity scale multiplier.
 *
 * Because acquisitionUnitCost is a RationalCost, the complete calculation
 * remains exact.
 */
class InventoryValuationService(
    private val inventoryCostLayerDao: InventoryCostLayerDao
) {

    /**
     * Calculates the exact total current inventory valuation across all
     * active inventory cost layers.
     *
     * Exhausted layers are excluded by the DAO's active-layer query.
     *
     * No intermediate rounding occurs.
     */
    fun calculateTotalValuation(): RationalCost {
        return inventoryCostLayerDao
            .getAllActiveLayers()
            .fold(zeroRationalCost()) { total, layer ->
                total.add(calculateLayerValuation(layer))
            }
    }

    /**
     * Calculates the exact current value represented by one inventory cost
     * layer.
     *
     * The calculation is:
     *
     *     acquisitionUnitCost × remainingStorageUnits / 10^scale
     *
     * The quantity scale is a precision representation, not a packaging
     * conversion. The scale multiplier therefore belongs in the valuation
     * denominator.
     */
    fun calculateLayerValuation(
        layer: InventoryCostLayer
    ): RationalCost {
        require(layer.initialQuantity.storageUnits > 0L) {
            "InventoryCostLayer initialQuantity must be strictly positive " +
                "for valuation, got: " +
                "${layer.initialQuantity.storageUnits} " +
                "(layerId=${layer.id})"
        }

        require(layer.remainingQuantity.storageUnits >= 0L) {
            "InventoryCostLayer remainingQuantity must be non-negative " +
                "for valuation, got: " +
                "${layer.remainingQuantity.storageUnits} " +
                "(layerId=${layer.id})"
        }

        require(
            layer.remainingQuantity.storageUnits <=
                layer.initialQuantity.storageUnits
        ) {
            "InventoryCostLayer remainingQuantity must not exceed " +
                "initialQuantity for valuation " +
                "(remaining=${layer.remainingQuantity.storageUnits}, " +
                "initial=${layer.initialQuantity.storageUnits}, " +
                "layerId=${layer.id})"
        }

        return layer.acquisitionUnitCost.multiply(
            numerator = BigInteger.valueOf(
                layer.remainingQuantity.storageUnits
            ),
            denominator = BigInteger.valueOf(
                layer.remainingQuantity.scale.multiplier
            )
        )
    }

    /**
     * Returns the canonical exact zero value used when no active inventory
     * layers exist.
     */
    private fun zeroRationalCost(): RationalCost {
        return RationalCost(
            numerator = BigInteger.ZERO,
            denominator = BigInteger.ONE
        )
    }
}
