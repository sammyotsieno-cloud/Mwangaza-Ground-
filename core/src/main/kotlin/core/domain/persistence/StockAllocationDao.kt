package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.domain.model.RationalCost
import core.domain.model.StockAllocation
import java.math.BigInteger

@Dao
interface StockAllocationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAllocation(allocation: StockAllocation)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAllocations(allocations: List<StockAllocation>)

    @Query(
        """
        SELECT * FROM stock_allocations
        WHERE consumption_transaction_id = :saleId
        ORDER BY allocated_at ASC
        """
    )
    fun getAllocationsForSale(saleId: String): List<StockAllocation>

    @Query(
        """
        SELECT * FROM stock_allocations
        WHERE consumption_item_id = :saleItemId
        ORDER BY allocated_at ASC
        """
    )
    fun getAllocationsForSaleItem(saleItemId: String): List<StockAllocation>

    @Query(
        """
        SELECT * FROM stock_allocations
        WHERE inventory_cost_layer_id = :layerId
        ORDER BY allocated_at ASC
        """
    )
    fun getAllocationsForCostLayer(layerId: String): List<StockAllocation>

    /**
     * Returns the exact COGS for completed sales of a product.
     *
     * SQL aggregation is deliberately avoided because allocated_cost is now
     * an arbitrary-precision RationalCost rather than a Long minor-unit value.
     * The exact values are aggregated after Room has reconstructed each
     * StockAllocation.
     */
    @Query(
        """
        SELECT sa.* FROM stock_allocations sa
        INNER JOIN sales s
            ON sa.consumption_transaction_id = s.id
        WHERE s.status = 'COMPLETED'
          AND sa.product_id = :productId
        ORDER BY sa.allocated_at ASC
        """
    )
    fun getEffectiveAllocationsForProduct(
        productId: String
    ): List<StockAllocation>

    /**
     * Returns the exact COGS for a completed sale.
     *
     * SQL aggregation is deliberately avoided for the same reason as above:
     * authoritative COGS must remain exact until the presentation or
     * settlement boundary.
     */
    @Query(
        """
        SELECT sa.* FROM stock_allocations sa
        INNER JOIN sales s
            ON sa.consumption_transaction_id = s.id
        WHERE s.id = :saleId
          AND s.status = 'COMPLETED'
        ORDER BY sa.allocated_at ASC
        """
    )
    fun getEffectiveAllocationsForSale(
        saleId: String
    ): List<StockAllocation>

    /**
     * Exact COGS aggregation for completed allocations belonging to a product.
     *
     * This is a regular DAO-level helper rather than a Room @Query because
     * RationalCost cannot safely be reduced through SQL SUM().
     */
    fun calculateEffectiveCogsForProduct(
        productId: String
    ): RationalCost {
        return getEffectiveAllocationsForProduct(productId)
            .fold(RationalCost(BigInteger.ZERO, BigInteger.ONE)) { total, allocation ->
                total.add(allocation.allocatedCost)
            }
    }

    /**
     * Exact COGS aggregation for a completed sale.
     */
    fun calculateEffectiveCogsForSale(
        saleId: String
    ): RationalCost {
        return getEffectiveAllocationsForSale(saleId)
            .fold(RationalCost(BigInteger.ZERO, BigInteger.ONE)) { total, allocation ->
                total.add(allocation.allocatedCost)
            }
    }
}
