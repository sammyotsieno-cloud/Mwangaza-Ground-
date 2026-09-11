package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import core.domain.model.InventoryCostLayer

@Dao
interface InventoryCostLayerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertLayer(layer: InventoryCostLayer)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertLayers(layers: List<InventoryCostLayer>)

    @Update
    fun updateLayer(layer: InventoryCostLayer)

    @Update
    fun updateLayers(layers: List<InventoryCostLayer>)

    @Query("SELECT * FROM inventory_cost_layers WHERE id = :id")
    fun getLayerById(id: String): InventoryCostLayer?

    @Query("SELECT * FROM inventory_cost_layers WHERE stock_batch_id = :batchId AND remaining_quantity_storage_units > 0 ORDER BY acquired_at ASC, created_at ASC, id ASC")
    fun getActiveLayersForBatch(batchId: String): List<InventoryCostLayer>

    @Query("SELECT * FROM inventory_cost_layers WHERE product_id = :productId AND remaining_quantity_storage_units > 0 ORDER BY acquired_at ASC, created_at ASC, id ASC")
    fun getActiveLayersForProduct(productId: String): List<InventoryCostLayer>

    @Query("SELECT * FROM inventory_cost_layers WHERE source_receipt_ref = :receiptRef")
    fun getLayersForReceiptRef(receiptRef: String): List<InventoryCostLayer>

    @Query("""
        UPDATE inventory_cost_layers
        SET remaining_quantity_storage_units = remaining_quantity_storage_units - :decrementUnits,
            updated_at = :updatedAt
        WHERE id = :layerId
          AND remaining_quantity_storage_units >= :decrementUnits
    """)
    fun decrementRemainingQuantity(layerId: String, decrementUnits: Long, updatedAt: Long): Int

    @Query("""
        UPDATE inventory_cost_layers
        SET remaining_quantity_storage_units = remaining_quantity_storage_units + :incrementUnits,
            updated_at = :updatedAt
        WHERE id = :layerId
          AND remaining_quantity_storage_units + :incrementUnits <= initial_quantity_storage_units
    """)
    fun incrementRemainingQuantity(layerId: String, incrementUnits: Long, updatedAt: Long): Int
}
