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
 *     acquisitionUnitCost × remainingQuantity
 *
 * acquisitionUnitCost is already expressed per storage unit, so no division
 * by initialQuantity is required.
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
     *     acquisitionUnitCost × remainingQuantity
     *
     * acquisitionUnitCost is already the exact acquisition cost per storage
     * unit, while remainingQuantity is the number of storage units currently
     * represented by the layer.
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
            BigInteger.valueOf(
                layer.remainingQuantity.storageUnits
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
