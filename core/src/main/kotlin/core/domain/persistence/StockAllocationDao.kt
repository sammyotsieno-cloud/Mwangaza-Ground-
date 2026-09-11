package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.domain.model.StockAllocation

@Dao
interface StockAllocationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAllocation(allocation: StockAllocation)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAllocations(allocations: List<StockAllocation>)

    @Query("SELECT * FROM stock_allocations WHERE consumption_transaction_id = :saleId ORDER BY allocated_at ASC")
    fun getAllocationsForSale(saleId: String): List<StockAllocation>

    @Query("SELECT * FROM stock_allocations WHERE consumption_item_id = :saleItemId ORDER BY allocated_at ASC")
    fun getAllocationsForSaleItem(saleItemId: String): List<StockAllocation>

    @Query("SELECT * FROM stock_allocations WHERE inventory_cost_layer_id = :layerId ORDER BY allocated_at ASC")
    fun getAllocationsForCostLayer(layerId: String): List<StockAllocation>
}
