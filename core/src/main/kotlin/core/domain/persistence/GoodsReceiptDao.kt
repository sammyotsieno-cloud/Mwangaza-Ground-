package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem

@Dao
interface GoodsReceiptDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertReceipt(receipt: GoodsReceipt)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertReceiptItems(items: List<GoodsReceiptItem>)

    @Update
    fun updateReceipt(receipt: GoodsReceipt)

    @Query("SELECT * FROM goods_receipts ORDER BY received_at DESC")
    fun getAllReceipts(): List<GoodsReceipt>

    @Query("SELECT * FROM goods_receipts WHERE id = :id")
    fun getReceiptById(id: String): GoodsReceipt?

    @Query("SELECT * FROM goods_receipts WHERE receipt_number = :receiptNumber")
    fun getReceiptByNumber(receiptNumber: String): GoodsReceipt?

    @Query("SELECT * FROM goods_receipt_items WHERE goods_receipt_id = :receiptId ORDER BY line_index ASC")
    fun getItemsForReceipt(receiptId: String): List<GoodsReceiptItem>
}
