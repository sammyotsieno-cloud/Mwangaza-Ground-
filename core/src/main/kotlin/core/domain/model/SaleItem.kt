package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an individual product line item within a [Sale].
 *
 * Core Concept & Snapshots:
 * - Answers: "Which product was sold, in what dispensing unit, at what selling price, and what was its COGS?"
 * - [unitPriceSnapshot] captures the exact price per commercial dispensing unit at transaction time.
 * - [lineCogs] preserves the exact mathematical cost of goods sold as a [RationalCost].
 * - Subsequent changes to [UnitPriceConfig] or [ProductMaster] do NOT rewrite historical sale items.
 * - Connects to physical batches and cost layers through one or more [StockAllocation] records.
 *
 * Financial precision:
 * - [unitPriceSnapshot] and [lineTotal] remain [Money] because they represent
 *   settled monetary values.
 * - [lineCogs] remains an exact rational value so authoritative COGS calculations
 *   are never performed from a UI-rounded monetary value.
 */
@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["sale_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductUnit::class,
            parentColumns = ["id"],
            childColumns = ["dispensing_unit_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["sale_id"]),
        Index(value = ["sale_id", "line_index"], unique = true),
        Index(value = ["product_id"]),
        Index(value = ["dispensing_unit_id"])
    ]
)
data class SaleItem(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "sale_id")
    val saleId: String,

    @ColumnInfo(name = "line_index")
    val lineIndex: Int,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "dispensing_unit_id")
    val dispensingUnitId: String,

    @Embedded(prefix = "requested_quantity_")
    val requestedQuantity: Quantity,

    @Embedded(prefix = "base_quantity_")
    val baseQuantity: Quantity,

    @ColumnInfo(name = "unit_price_snapshot")
    val unitPriceSnapshot: Money,

    @ColumnInfo(name = "line_total")
    val lineTotal: Money,

    @ColumnInfo(name = "line_cogs")
    val lineCogs: RationalCost,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "SaleItem id must not be blank or contain leading/trailing whitespace"
        }
        require(saleId.isNotBlank() && saleId.trim() == saleId) {
            "SaleItem saleId must not be blank or contain leading/trailing whitespace"
        }
        require(lineIndex >= 0) {
            "SaleItem lineIndex must be non-negative (>= 0), got: $lineIndex (id=$id)"
        }
        require(productId.isNotBlank() && productId.trim() == productId) {
            "SaleItem productId must not be blank or contain leading/trailing whitespace"
        }
        require(dispensingUnitId.isNotBlank() && dispensingUnitId.trim() == dispensingUnitId) {
            "SaleItem dispensingUnitId must not be blank or contain leading/trailing whitespace"
        }
        require(requestedQuantity.isPositive) {
            "SaleItem requestedQuantity must be strictly positive (> 0), got: ${requestedQuantity.storageUnits} (id=$id)"
        }
        require(baseQuantity.isPositive) {
            "SaleItem baseQuantity must be strictly positive (> 0), got: ${baseQuantity.storageUnits} (id=$id)"
        }
        require(unitPriceSnapshot.amountMinorUnits >= 0L) {
            "SaleItem unitPriceSnapshot must not be negative, got: ${unitPriceSnapshot.amountMinorUnits} (id=$id)"
        }
        require(lineTotal.amountMinorUnits >= 0L) {
            "SaleItem lineTotal must not be negative, got: ${lineTotal.amountMinorUnits} (id=$id)"
        }
        require(lineCogs.isNonNegative) {
            "SaleItem lineCogs must not be negative, got: $lineCogs (id=$id)"
        }
        require(createdAt > 0L) {
            "SaleItem createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
        require(updatedAt >= createdAt) {
            "SaleItem updatedAt ($updatedAt) must not precede createdAt ($createdAt) (id=$id)"
        }
    }
}
