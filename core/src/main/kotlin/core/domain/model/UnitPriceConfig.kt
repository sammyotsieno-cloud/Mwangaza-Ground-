package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing the CURRENT configured selling price for a specific [ProductUnit].
 *
 * Architectural Purpose:
 * - Answers: "For this product, what is the current configured selling price when sold in this commercial unit?"
 * - Exactly one current configuration exists per [ProductUnit], enforced by a unique index on [productUnitId].
 * - Represents current configuration ONLY. Historical price changes belong to future [PriceHistory].
 * - Purchase/acquisition costs belong to [GoodsReceipt] and [InventoryCostLayer].
 * - Historical COGS belongs to [StockAllocation].
 * - Historical transaction selling prices are snapshotted into [SaleItem].
 * - Changing [sellingPrice] here does NOT rewrite or alter historical sales records.
 *
 * Monetary Exactness:
 * - [sellingPrice] is represented as an exact [Money] value object (Phase 1 KES, 2 decimal places).
 * - Persisted via [RoomConverters] as a 64-bit integer ([Money.amountMinorUnits] in Long).
 * - Zero floating-point types (no Double, Float, or BigDecimal).
 *
 * Foreign Key Policy:
 * - References [ProductUnit.id] with [ForeignKey.RESTRICT] to protect pricing configurations against
 *   accidental physical deletion of operational units.
 */
@Entity(
    tableName = "unit_price_configs",
    foreignKeys = [
        ForeignKey(
            entity = ProductUnit::class,
            parentColumns = ["id"],
            childColumns = ["product_unit_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_unit_id"], unique = true)
    ]
)
data class UnitPriceConfig(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "product_unit_id")
    val productUnitId: String,

    @ColumnInfo(name = "selling_price")
    val sellingPrice: Money,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank()) { "UnitPriceConfig id must not be blank" }
        require(productUnitId.isNotBlank()) { "UnitPriceConfig productUnitId must not be blank" }
        require(sellingPrice.amountMinorUnits >= 0L) {
            "UnitPriceConfig sellingPrice must be non-negative (>= 0), got: ${sellingPrice.amountMinorUnits} minor units (id=$id)"
        }
    }
}
