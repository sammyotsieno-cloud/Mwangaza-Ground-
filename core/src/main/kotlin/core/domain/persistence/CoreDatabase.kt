package core.domain.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import core.domain.model.FacilityProfile
import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.InventoryCostLayer
import core.domain.model.PharmaceuticalDetail
import core.domain.model.PriceHistory
import core.domain.model.ProductCategory
import core.domain.model.ProductImage
import core.domain.model.ProductMaster
import core.domain.model.ProductTag
import core.domain.model.ProductTagAssignment
import core.domain.model.ProductUnit
import core.domain.model.RoomConverters
import core.domain.model.Sale
import core.domain.model.SaleItem
import core.domain.model.StockAllocation
import core.domain.model.StockBatch
import core.domain.model.StockMovement
import core.domain.model.Supplier
import core.domain.model.UnitPriceConfig

/**
 * Room Database definition for core inventory, receiving, dispensing, and ledger transactions.
 */
@Database(
    entities = [
        ProductCategory::class,
        ProductMaster::class,
        ProductUnit::class,
        PharmaceuticalDetail::class,
        ProductImage::class,
        ProductTag::class,
        ProductTagAssignment::class,
        UnitPriceConfig::class,
        PriceHistory::class,
        FacilityProfile::class,
        Supplier::class,
        GoodsReceipt::class,
        GoodsReceiptItem::class,
        StockBatch::class,
        InventoryCostLayer::class,
        StockMovement::class,
        StockAllocation::class,
        Sale::class,
        SaleItem::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class CoreDatabase : RoomDatabase() {
    abstract fun goodsReceiptDao(): GoodsReceiptDao
    abstract fun stockBatchDao(): StockBatchDao
    abstract fun inventoryCostLayerDao(): InventoryCostLayerDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun stockAllocationDao(): StockAllocationDao
    abstract fun saleDao(): SaleDao
    abstract fun productMasterDao(): ProductMasterDao

    companion object {
        @Volatile
        private var INSTANCE: CoreDatabase? = null

        fun getInstance(context: android.content.Context): CoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    CoreDatabase::class.java,
                    "mwangaza_ground.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
