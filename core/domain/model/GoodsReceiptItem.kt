package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an individual product line within a [GoodsReceipt].
 *
 * Core Concept:
 * - Represents: "What product was received, in what commercial unit, at what actual purchase price,
 *   with what batch identity and expiry date?"
 * - Captures receipt-level inputs required to generate:
 *     1. [StockBatch] (physical/traceability lot)
 *     2. [InventoryCostLayer] (acquisition cost tranche)
 *     3. [StockMovement] (physical stock inflow)
 * - Canonical product identity is referenced via [productId] ([ProductMaster]); product descriptive fields
 *   are NOT redundantly copied here.
 * - Commercial packaging is referenced via [receivingUnitId] ([ProductUnit]).
 *
 * Cost & Monetary Exactness:
 * - [unitCost] is the actual buying price per receiving commercial unit (represented as exact [Money]).
 * - [totalCost] is the exact total acquisition cost for this line ([Money]).
 * - Calculations are strictly integer/minor-unit based in Phase-1 KES cents; zero Double/Float.
 *
 * Batch & Expiry Rules:
 * - [batchNumber]: Manufacturer batch/lot number, or null/blank if untracked/non-batched.
 * - [expiryDateInt]: Integer YYYYMMDD (real Gregorian calendar date) or -1 if unknown/non-expiring.
 * - [trackingMode]: One of:
 *     * [StockBatch.TRACKING_STANDARD_BATCHED]
 *     * [StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY]
 *     * [StockBatch.TRACKING_SUPPLIER_UNTRACKED]
 *     * [StockBatch.TRACKING_NON_BATCHED_COMMODITY]
 */
@Entity(
    tableName = "goods_receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = GoodsReceipt::class,
            parentColumns = ["id"],
            childColumns = ["goods_receipt_id"],
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
            childColumns = ["receiving_unit_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["goods_receipt_id"]),
        Index(value = ["goods_receipt_id", "line_index"], unique = true),
        Index(value = ["product_id"]),
        Index(value = ["receiving_unit_id"])
    ]
)
data class GoodsReceiptItem(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "goods_receipt_id")
    val goodsReceiptId: String,

    @ColumnInfo(name = "line_index")
    val lineIndex: Int,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "receiving_unit_id")
    val receivingUnitId: String,

    @Embedded(prefix = "received_quantity_")
    val receivedQuantity: Quantity,

    @ColumnInfo(name = "unit_cost")
    val unitCost: Money,

    @ColumnInfo(name = "total_cost")
    val totalCost: Money,

    @ColumnInfo(name = "batch_number")
    val batchNumber: String? = null,

    @ColumnInfo(name = "expiry_date_int")
    val expiryDateInt: Int = StockBatch.EXPIRY_UNKNOWN_OR_NONE,

    @ColumnInfo(name = "tracking_mode")
    val trackingMode: String = StockBatch.TRACKING_STANDARD_BATCHED,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "GoodsReceiptItem id must not be blank or contain leading/trailing whitespace"
        }
        require(goodsReceiptId.isNotBlank() && goodsReceiptId.trim() == goodsReceiptId) {
            "GoodsReceiptItem goodsReceiptId must not be blank or contain leading/trailing whitespace"
        }
        require(lineIndex >= 0) {
            "GoodsReceiptItem lineIndex must be non-negative (>= 0), got: $lineIndex (id=$id)"
        }
        require(productId.isNotBlank() && productId.trim() == productId) {
            "GoodsReceiptItem productId must not be blank or contain leading/trailing whitespace"
        }
        require(receivingUnitId.isNotBlank() && receivingUnitId.trim() == receivingUnitId) {
            "GoodsReceiptItem receivingUnitId must not be blank or contain leading/trailing whitespace"
        }
        require(receivedQuantity.isPositive) {
            "GoodsReceiptItem receivedQuantity must be strictly positive (> 0), got: ${receivedQuantity.storageUnits} (id=$id)"
        }
        require(unitCost.amountMinorUnits >= 0L) {
            "GoodsReceiptItem unitCost must not be negative, got: ${unitCost.amountMinorUnits} (id=$id)"
        }
        require(totalCost.amountMinorUnits >= 0L) {
            "GoodsReceiptItem totalCost must not be negative, got: ${totalCost.amountMinorUnits} (id=$id)"
        }
        require(StockBatch.isValidExpiryDateInt(expiryDateInt)) {
            "GoodsReceiptItem expiryDateInt must be -1 or a valid Gregorian calendar date in YYYYMMDD format, got: $expiryDateInt (id=$id)"
        }
        require(
            trackingMode == StockBatch.TRACKING_STANDARD_BATCHED ||
            trackingMode == StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY ||
            trackingMode == StockBatch.TRACKING_SUPPLIER_UNTRACKED ||
            trackingMode == StockBatch.TRACKING_NON_BATCHED_COMMODITY
        ) {
            "Invalid GoodsReceiptItem trackingMode: '$trackingMode' (id=$id)"
        }

        when (trackingMode) {
            StockBatch.TRACKING_STANDARD_BATCHED -> {
                require(!batchNumber.isNullOrBlank()) {
                    "STANDARD_BATCHED item requires a non-blank batchNumber (id=$id)"
                }
                require(expiryDateInt != StockBatch.EXPIRY_UNKNOWN_OR_NONE) {
                    "STANDARD_BATCHED item requires a valid expiry date, not -1 (id=$id)"
                }
            }
            StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY -> {
                require(!batchNumber.isNullOrBlank()) {
                    "BATCH_UNKNOWN_EXPIRY item requires a non-blank batchNumber (id=$id)"
                }
                require(expiryDateInt == StockBatch.EXPIRY_UNKNOWN_OR_NONE) {
                    "BATCH_UNKNOWN_EXPIRY item must have expiryDateInt = -1, got: $expiryDateInt (id=$id)"
                }
            }
            StockBatch.TRACKING_NON_BATCHED_COMMODITY -> {
                require(expiryDateInt == StockBatch.EXPIRY_UNKNOWN_OR_NONE) {
                    "NON_BATCHED_COMMODITY item must have expiryDateInt = -1, got: $expiryDateInt (id=$id)"
                }
            }
            StockBatch.TRACKING_SUPPLIER_UNTRACKED -> {
                // Batch number will be generated during receiving workflow if absent
            }
        }

        require(createdAt > 0L) {
            "GoodsReceiptItem createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
        require(updatedAt > 0L) {
            "GoodsReceiptItem updatedAt must be a positive epoch timestamp, got: $updatedAt (id=$id)"
        }
        require(updatedAt >= createdAt) {
            "GoodsReceiptItem updatedAt ($updatedAt) must not precede createdAt ($createdAt) (id=$id)"
        }
    }
}
