package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.domain.model.StockBatch

@Dao
interface StockBatchDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBatch(batch: StockBatch)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBatches(batches: List<StockBatch>)

    @Query("SELECT * FROM stock_batches ORDER BY expiry_date_int ASC, created_at ASC")
    fun getAllBatches(): List<StockBatch>

    @Query("SELECT * FROM stock_batches WHERE id = :id")
    fun getBatchById(id: String): StockBatch?

    @Query("SELECT * FROM stock_batches WHERE product_id = :productId ORDER BY expiry_date_int ASC, created_at ASC")
    fun getBatchesForProduct(productId: String): List<StockBatch>

    @Query("SELECT * FROM stock_batches WHERE product_id = :productId AND batch_number = :batchNumber AND expiry_date_int = :expiryDateInt LIMIT 1")
    fun findMatchingBatch(productId: String, batchNumber: String, expiryDateInt: Int): StockBatch?
}
