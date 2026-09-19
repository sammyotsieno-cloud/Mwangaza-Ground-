package core.domain.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import core.domain.model.FacilityProfile
import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.InventoryCostLayer
import core.domain.model.PharmaceuticalDetail
import core.domain.model.PriceHistory
import core.domain.model.ProductCategory
import core.domain.model.ProductImage
import core.domain.model.ProductIngredient
import core.domain.model.ProductIdentifier
import core.domain.model.ProductEntity
import core.domain.model.ProductAttribute
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
 * Room Database definition for core inventory, receiving, dispensing,
 * and ledger transactions.
 *
 * Persistence responsibility:
 * - Registers the authoritative Room entities.
 * - Registers the authoritative domain type converters.
 * - Owns database schema versioning.
 * - Owns schema migrations required when persisted domain semantics change.
 *
 * This class does NOT contain:
 * - business validation;
 * - stock calculation;
 * - FEFO logic;
 * - COGS calculation;
 * - receipt-number generation;
 * - reporting logic;
 * - quantity conversion logic.
 *
 * Historical data must survive schema evolution without destructive
 * recreation of the database.
 */
@Database(
    entities = [
        ProductCategory::class,
        ProductMaster::class,
        ProductUnit::class,
        PharmaceuticalDetail::class,
        ProductImage::class,
        ProductIngredient::class,
        ProductIdentifier::class,
        ProductEntity::class,
        ProductAttribute::class,
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
    version = 3,
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

        /**
         * v1 -> v2
         *
         * ProductMaster gained two persisted quantity-policy fields:
         *
         * 1. quantity_scale
         * 2. minimum_transaction_increment_storage_units
         *
         * Existing v1 products used whole-unit quantity semantics.
         *
         * Therefore the migration preserves their historical meaning by
         * assigning:
         *
         * quantity_scale = 0
         * minimum_transaction_increment_storage_units = 1
         *
         * No existing quantity, money, stock, receipt, batch, cost-layer,
         * or transaction record is rewritten.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE product_masters
                    ADD COLUMN quantity_scale INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    ALTER TABLE product_masters
                    ADD COLUMN minimum_transaction_increment_storage_units
                    INTEGER NOT NULL DEFAULT 1
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS product_ingredients (
                        id TEXT NOT NULL,
                        product_id TEXT NOT NULL,
                        ingredient_name TEXT NOT NULL,
                        normalized_ingredient_name TEXT,
                        strength_value TEXT,
                        strength_unit TEXT,
                        denominator_value TEXT,
                        denominator_unit TEXT,
                        sequence INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(product_id) REFERENCES product_masters(id) ON DELETE RESTRICT ON UPDATE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_ingredients_product_id ON product_ingredients(product_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_ingredients_product_id_sequence ON product_ingredients(product_id, sequence)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS product_identifiers (
                        id TEXT NOT NULL,
                        product_id TEXT NOT NULL,
                        identifier_type TEXT NOT NULL,
                        value TEXT NOT NULL,
                        normalized_value TEXT NOT NULL,
                        is_primary INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(product_id) REFERENCES product_masters(id) ON DELETE RESTRICT ON UPDATE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_identifiers_product_id ON product_identifiers(product_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_product_identifiers_identifier_type_normalized_value ON product_identifiers(identifier_type, normalized_value)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS product_entities (
                        id TEXT NOT NULL,
                        product_id TEXT NOT NULL,
                        entity_name TEXT NOT NULL,
                        normalized_name TEXT,
                        role TEXT NOT NULL,
                        location TEXT,
                        address TEXT,
                        sequence INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(product_id) REFERENCES product_masters(id) ON DELETE RESTRICT ON UPDATE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_entities_product_id ON product_entities(product_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_entities_product_id_role_sequence ON product_entities(product_id, role, sequence)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS product_attributes (
                        id TEXT NOT NULL,
                        product_id TEXT NOT NULL,
                        definition_key TEXT NOT NULL,
                        value_type TEXT NOT NULL,
                        value TEXT NOT NULL,
                        normalized_value TEXT,
                        provenance TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(product_id) REFERENCES product_masters(id) ON DELETE RESTRICT ON UPDATE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_attributes_product_id ON product_attributes(product_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_attributes_product_id_definition_key ON product_attributes(product_id, definition_key)")
            }
        }

        @Volatile
        private var INSTANCE: CoreDatabase? = null

        fun getInstance(context: android.content.Context): CoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    CoreDatabase::class.java,
                    "mwangaza_ground.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
