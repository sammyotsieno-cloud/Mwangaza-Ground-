package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Immutable historical selling-price validity interval for a specific ProductUnit.
 *
 * INTERVAL SEMANTICS
 * ------------------
 * [effectiveFrom, effectiveTo)
 *
 * - effectiveFrom is inclusive.
 * - effectiveTo is exclusive.
 * - effectiveTo == null means the interval is open-ended.
 * - When effectiveTo is present, it must be strictly greater than
 *   effectiveFrom.
 *
 * AUTHORITY
 * ---------
 * UnitPriceConfig
 *     → authoritative CURRENT selling-price configuration.
 *
 * PriceHistory
 *     → chronological HISTORICAL selling-price validity timeline.
 *
 * SaleItem
 *     → actual selling price charged at a completed transaction.
 *
 * PriceHistory therefore must never be used as an acquisition-cost,
 * inventory-valuation, or COGS authority.
 *
 * ACQUISITION COST SEPARATION
 * ---------------------------
 * Actual purchase/acquisition cost belongs to:
 *
 * GoodsReceipt
 *     +
 * InventoryCostLayer
 *
 * Historical COGS belongs to StockAllocation.
 *
 * INTERVAL INTEGRITY
 * ------------------
 * The entity enforces the validity of ONE interval.
 *
 * Cross-record invariants remain at the pricing transaction boundary:
 *
 * 1. No overlapping intervals for the same ProductUnit.
 * 2. No gaps between consecutive committed intervals.
 * 3. At most one open-ended interval per ProductUnit.
 * 4. Closing the previous interval, creating the successor interval,
 *    and updating UnitPriceConfig must occur atomically.
 *
 * These invariants require repository/service context and therefore
 * must NOT be duplicated inside this entity.
 *
 * TIME SEMANTICS
 * --------------
 * effectiveFrom, effectiveTo, and createdAt are absolute epoch
 * millisecond timestamps.
 *
 * Facility-local interpretation belongs to the centralized time
 * architecture and FacilityProfile.
 *
 * BACKDATING
 * ----------
 * createdAt is deliberately NOT required to be greater than or equal
 * to effectiveFrom.
 *
 * A historical price correction or backdated price interval may be
 * legitimate, provided the pricing workflow maintains the global
 * interval invariants.
 *
 * MONETARY EXACTNESS
 * ------------------
 * sellingPrice uses the exact Money value object.
 *
 * - KES in Phase 1.
 * - Two decimal places.
 * - No Float or Double.
 * - No floating-point calculations.
 * - Negative prices are prohibited.
 * - Zero prices remain valid for legitimate free/promotional
 *   distribution.
 *
 * FOREIGN KEY POLICY
 * ------------------
 * References ProductUnit with RESTRICT deletion so historical pricing
 * records cannot be orphaned by accidental ProductUnit deletion.
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
        require(id.isNotBlank() && id.trim() == id) {
            "PriceHistory id must not be blank or contain leading/trailing whitespace"
        }

        require(productUnitId.isNotBlank() && productUnitId.trim() == productUnitId) {
            "PriceHistory productUnitId must not be blank or contain leading/trailing whitespace"
        }

        require(sellingPrice.amountMinorUnits >= 0L) {
            "PriceHistory sellingPrice must be non-negative (>= 0), " +
                "got: ${sellingPrice.amountMinorUnits} minor units (id=$id)"
        }

        require(effectiveFrom > 0L) {
            "PriceHistory effectiveFrom must be a positive epoch timestamp, " +
                "got: $effectiveFrom (id=$id)"
        }

        if (effectiveTo != null) {
            require(effectiveTo > effectiveFrom) {
                "PriceHistory effectiveTo ($effectiveTo) must be strictly greater " +
                    "than effectiveFrom ($effectiveFrom) (id=$id)"
            }
        }

        require(createdAt > 0L) {
            "PriceHistory createdAt must be a positive epoch timestamp, " +
                "got: $createdAt (id=$id)"
        }
    }
}
