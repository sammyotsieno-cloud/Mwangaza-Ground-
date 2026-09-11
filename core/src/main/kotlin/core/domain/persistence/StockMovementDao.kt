package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.domain.model.StockMovement

@Dao
interface StockMovementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMovement(movement: StockMovement)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMovements(movements: List<StockMovement>)

    @Query("SELECT * FROM stock_movements WHERE product_id = :productId ORDER BY occurred_at ASC, created_at ASC")
    fun getMovementsForProduct(productId: String): List<StockMovement>

    @Query("SELECT * FROM stock_movements WHERE stock_batch_id = :batchId ORDER BY occurred_at ASC, created_at ASC")
    fun getMovementsForBatch(batchId: String): List<StockMovement>

    @Query("SELECT COALESCE(SUM(quantity_storage_units), 0) FROM stock_movements WHERE product_id = :productId")
    fun getPhysicalStockUnitsForProduct(productId: String): Long

    @Query("SELECT COALESCE(SUM(quantity_storage_units), 0) FROM stock_movements WHERE stock_batch_id = :batchId")
    fun getPhysicalStockUnitsForBatch(batchId: String): Long

    @Query("SELECT * FROM stock_movements WHERE source_transaction_ref = :ref")
    fun getMovementsBySourceRef(ref: String): List<StockMovement>
}
