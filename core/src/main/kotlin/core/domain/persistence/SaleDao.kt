package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import core.domain.model.Sale
import core.domain.model.SaleItem

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSale(sale: Sale)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSaleItems(items: List<SaleItem>)

    @Update
    fun updateSale(sale: Sale)

    @Query("SELECT * FROM sales ORDER BY occurred_at DESC")
    fun getAllSales(): List<Sale>

    @Query("SELECT * FROM sales WHERE id = :id")
    fun getSaleById(id: String): Sale?

    @Query("SELECT * FROM sales WHERE sale_number = :saleNumber")
    fun getSaleByNumber(saleNumber: String): Sale?

    @Query("SELECT * FROM sale_items WHERE sale_id = :saleId ORDER BY line_index ASC")
    fun getItemsForSale(saleId: String): List<SaleItem>
}
