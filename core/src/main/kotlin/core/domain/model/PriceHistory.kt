package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an immutable historical selling-price validity interval for a specific [ProductUnit].
 *
 * Interval Semantics — [effectiveFrom, effectiveTo):
 * - [effectiveFrom] is INCLUSIVE: The price becomes valid at exactly this instant.
 * - [effectiveTo] is EXCLUSIVE: The price ceases to be valid at exactly this instant.
 * - If [effectiveTo] == null, the interval is open-ended (valid indefinitely until closed by a successor price).
 * - Represents a non-empty interval: When [effectiveTo] is specified, it must satisfy [effectiveTo] > [effectiveFrom].
 *
 * Future Database / Service Invariants (enforced at service/repository transaction boundary):
 * 1. No Overlaps: For a given [productUnitId], committed PriceHistory intervals must never overlap.
 * 2. No Gaps: Consecutive price intervals meet exactly (previous.effectiveTo == next.effectiveFrom).
 * 3. Single Open-Ended Interval: Only one open-ended interval (effectiveTo == null) can exist per [ProductUnit].
 * 4. Atomic Transition: When a price changes, closing the previous [PriceHistory] interval, opening the new
 *    [PriceHistory] interval, and updating [UnitPriceConfig] must commit atomically.
 *
 * Authority & Separation of Concerns:
 * - [UnitPriceConfig] is the authoritative CURRENT selling-price configuration.
 * - [PriceHistory] is the chronological HISTORICAL validity timeline.
 * - [SaleItem] snapshots the actual transaction price charged at checkout.
 * - [GoodsReceipt] and [InventoryCostLayer] record actual purchase/acquisition costs.
 * - [StockAllocation] tracks historical Cost of Goods Sold (COGS).
 *
 * Time & Calendar Architecture:
 * - [effectiveFrom], [effectiveTo], and [createdAt] are epoch milliseconds (Long) representing absolute instants.
 * - Interpretation into facility-local dates and times is governed by [FacilityProfile.facilityTimezone]
 *   (Phase 1 default: "Africa/Nairobi") via a future centralized time provider abstraction.
 * - Does NOT require or use Android Calendar permissions (READ_CALENDAR / WRITE_CALENDAR).
 * - Offline-first: Operates locally using the device clock as the time source without mandatory network time synchronization.
 *
 * Monetary Exactness:
 * - [sellingPrice] is stored as an exact [Money] value object (Phase 1 KES, 2 decimal places).
 * - Persisted via [RoomConverters] as a 64-bit integer ([Money.amountMinorUnits] in Long).
 * - Zero floating-point arithmetic (no Double, Float, or BigDecimal).
 * - Zero prices are permitted for promotional, sample, or free community distribution; negative prices are rejected.
 *
 * Foreign Key Policy:
 * - References [ProductUnit.id] with [ForeignKey.RESTRICT] to protect audit history against accidental deletion.
 */
@Entity(
    tableName = "price_histories",
    foreignKeys = [
        ForeignKey(
            entity = ProductUnit::class,
            parentColumns = ["id"],
            childColumns = ["product_unit_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_unit_id"]),
        Index(value = ["product_unit_id", "effective_from"])
    ]
)
data class PriceHistory(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "product_unit_id")
    val productUnitId: String,

    @ColumnInfo(name = "selling_price")
    val sellingPrice: Money,

    @ColumnInfo(name = "effective_from")
    val effectiveFrom: Long,

    @ColumnInfo(name = "effective_to")
    val effectiveTo: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    init {
        require(id.isNotBlank()) { "PriceHistory id must not be blank" }
        require(productUnitId.isNotBlank()) { "PriceHistory productUnitId must not be blank" }
        require(sellingPrice.amountMinorUnits >= 0L) {
            "PriceHistory sellingPrice must be non-negative (>= 0), got: ${sellingPrice.amountMinorUnits} minor units (id=$id)"
        }
        require(effectiveFrom > 0L) {
            "PriceHistory effectiveFrom must be a positive epoch timestamp, got: $effectiveFrom (id=$id)"
        }
        if (effectiveTo != null) {
            require(effectiveTo > effectiveFrom) {
                "PriceHistory effectiveTo ($effectiveTo) must be strictly greater than effectiveFrom ($effectiveFrom) (id=$id)"
            }
        }
        require(createdAt > 0L) {
            "PriceHistory createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
    }
}
