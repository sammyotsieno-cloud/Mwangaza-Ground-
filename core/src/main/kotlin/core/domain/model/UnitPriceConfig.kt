package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing the CURRENT configured selling price for a specific
 * ProductUnit.
 *
 * AUTHORITY
 * ---------
 * UnitPriceConfig answers:
 *
 *     "What is the currently configured selling price for this commercial
 *      product unit?"
 *
 * Exactly one current configuration exists per ProductUnit, enforced by the
 * unique product_unit_id index.
 *
 * SEPARATION OF CONCERNS
 * ----------------------
 * UnitPriceConfig is selling-price configuration only.
 *
 * It does NOT represent:
 * - acquisition/purchase cost;
 * - historical acquisition cost;
 * - inventory value;
 * - COGS;
 * - stock quantity;
 * - FEFO;
 * - transaction history;
 * - historical selling-price intervals.
 *
 * Acquisition costs belong to GoodsReceipt and InventoryCostLayer.
 * Historical selling-price intervals belong to PriceHistory.
 * Actual transaction selling prices belong to SaleItem.
 *
 * Changing this configuration therefore must never rewrite historical
 * transaction prices.
 *
 * MONETARY EXACTNESS
 * ------------------
 * sellingPrice is represented by the exact Money value object.
 *
 * Phase 1:
 * - currency: KES
 * - precision: two decimal places
 * - persistence: Long minor currency units
 * - floating-point arithmetic: prohibited
 *
 * A zero selling price is permitted for legitimate zero-price distribution,
 * promotional or community-distribution scenarios.
 * Negative selling prices are prohibited.
 *
 * PRODUCT UNIT RELATIONSHIP
 * -------------------------
 * productUnitId identifies the commercial unit being priced.
 *
 * The commercial unit's conversion to the product's canonical quantity is
 * owned by ProductUnit and the quantity/conversion domain.
 *
 * UnitPriceConfig does NOT perform that conversion.
 *
 * TIMESTAMP SEMANTICS
 * -------------------
 * createdAt and updatedAt are positive epoch timestamps.
 *
 * updatedAt must never precede createdAt.
 *
 * Historical price-transition atomicity remains the responsibility of the
 * pricing service/repository boundary:
 *
 * PriceHistory transition
 *        +
 * UnitPriceConfig update
 *        =
 * one atomic operation.
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
        require(id.isNotBlank() && id.trim() == id) {
            "UnitPriceConfig id must not be blank or contain leading/trailing whitespace"
        }

        require(productUnitId.isNotBlank() && productUnitId.trim() == productUnitId) {
            "UnitPriceConfig productUnitId must not be blank or contain leading/trailing whitespace"
        }

        require(sellingPrice.amountMinorUnits >= 0L) {
            "UnitPriceConfig sellingPrice must be non-negative (>= 0), " +
                "got: ${sellingPrice.amountMinorUnits} minor units (id=$id)"
        }

        require(createdAt > 0L) {
            "UnitPriceConfig createdAt must be a positive epoch timestamp, " +
                "got: $createdAt (id=$id)"
        }

        require(updatedAt > 0L) {
            "UnitPriceConfig updatedAt must be a positive epoch timestamp, " +
                "got: $updatedAt (id=$id)"
        }

        require(updatedAt >= createdAt) {
            "UnitPriceConfig updatedAt ($updatedAt) must not precede " +
                "createdAt ($createdAt) (id=$id)"
        }
    }
}
