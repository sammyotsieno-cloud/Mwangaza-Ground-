package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing one product line on a [GoodsReceipt].
 *
 * ROLE
 * ----
 * GoodsReceiptItem captures the acquisition facts supplied by the receiving
 * workflow:
 *
 * - which product was received;
 * - which commercial receiving unit was used;
 * - how much was received;
 * - the supplier/invoice unit-price snapshot;
 * - the exact acquisition cost for the line;
 * - physical batch/expiry information;
 * - receiving-line timestamps.
 *
 * It is deliberately NOT responsible for:
 *
 * - converting commercial quantities to canonical quantities;
 * - deciding whether a quantity is legal for a product;
 * - calculating stock balances;
 * - creating StockMovement records;
 * - creating InventoryCostLayer records;
 * - allocating COGS;
 * - FEFO selection;
 * - validating supplier identity;
 * - persisting or committing a receipt.
 *
 * Those responsibilities belong to the receiving validation/service and
 * persistence layers.
 *
 * QUANTITY SEMANTICS
 * ------------------
 * receivedQuantity is the canonical physical quantity represented using
 * Quantity's exact storage-unit + QuantityScale model.
 *
 * The product-specific quantity policy belongs to ProductMaster:
 *
 * ProductMaster
 *     -> canonical quantity scale + minimum transaction increment
 *
 * ProductUnit
 *     -> commercial unit + exact rational conversion
 *
 * Quantity
 *     -> exact mathematical quantity representation
 *
 * GoodsReceiptItem
 *     -> the canonical quantity actually recorded for this receipt line
 *
 * The receiving workflow must therefore verify that:
 *
 * 1. receivingUnitId belongs to productId;
 * 2. the receiving unit is compatible with the product's canonical unit;
 * 3. commercial quantity converts exactly to receivedQuantity;
 * 4. receivedQuantity uses ProductMaster.quantityScale;
 * 5. receivedQuantity satisfies ProductMaster's minimum transaction
 *    increment.
 *
 * Those cross-entity checks intentionally remain outside this entity.
 *
 * COST SEMANTICS
 * --------------
 * totalCost is the authoritative acquisition cost for this receipt line.
 *
 * unitCost is retained as a supplier/invoice unit-price snapshot or nominal
 * commercial-unit cost supplied by the receiving workflow.
 *
 * unitCost is NOT the monetary authority for the receipt line and is NOT
 * required to reconstruct totalCost exactly in currency minor units.
 *
 * This distinction is necessary because a legitimate acquisition total may
 * be indivisible across the received quantity when represented in the
 * smallest monetary unit.
 *
 * Example:
 *
 *     quantity = 3
 *     totalCost = 100 minor units
 *
 * The exact acquisition total remains 100 even though 100 / 3 is not an
 * integral number of minor currency units.
 *
 * GoodsReceiptService is responsible for converting the authoritative
 * totalCost into exact InventoryCostLayer cost tranches whose combined
 * monetary value equals totalCost exactly.
 *
 * Neither field represents:
 * - selling price;
 * - current inventory value;
 * - COGS;
 * - a cost-layer balance.
 *
 * InventoryCostLayer is created from these acquisition facts by the
 * receiving persistence workflow.
 *
 * BATCH / EXPIRY SEMANTICS
 * ------------------------
 * expiryDateInt stores a normalized YYYYMMDD Gregorian date, or
 * StockBatch.EXPIRY_UNKNOWN_OR_NONE (-1).
 *
 * Month/year-only manufacturer dates are normalized by ExpiryPolicy before
 * this entity is constructed. For example:
 *
 *     2026-02 -> 20260201
 *     02/2026 -> 20260201
 *
 * This entity therefore stores the normalized domain representation rather
 * than raw user input.
 *
 * Tracking-mode invariants:
 *
 * STANDARD_BATCHED
 *     -> requires a batch number and a known expiry date.
 *
 * BATCH_UNKNOWN_EXPIRY
 *     -> requires a batch number and expiry = -1.
 *
 * SUPPLIER_UNTRACKED
 *     -> may have a batch number supplied by the workflow, but does not
 *        require one at the domain-input boundary.
 *
 * NON_BATCHED_COMMODITY
 *     -> expiry = -1 and no batch identity is required.
 *
 * The meaning of -1 is deliberately retained as the existing domain
 * sentinel for unknown expiry OR genuinely non-expiring stock.
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

    /**
     * Zero-based line position within the receipt.
     *
     * Uniqueness is enforced together with goodsReceiptId by the Room index.
     */
    @ColumnInfo(name = "line_index")
    val lineIndex: Int,

    /**
     * Canonical product identity.
     *
     * Product quantity policy is obtained from ProductMaster.
     */
    @ColumnInfo(name = "product_id")
    val productId: String,

    /**
     * Commercial unit in which the supplier supplied the product.
     *
     * The exact conversion into receivedQuantity is owned by ProductUnit
     * and the receiving workflow.
     */
    @ColumnInfo(name = "receiving_unit_id")
    val receivingUnitId: String,

    /**
     * Exact canonical physical quantity recorded for this receipt line.
     *
     * The scale must agree with ProductMaster.quantityScale.
     *
     * Cross-entity scale and minimum-increment validation is deliberately
     * performed by GoodsReceiptValidation.
     */
    @Embedded(prefix = "received_quantity_")
    val receivedQuantity: Quantity,

    /**
     * Supplier/invoice unit-price snapshot or nominal acquisition price
     * expressed per receiving commercial unit.
     *
     * This value is retained as acquisition context and is not the
     * authoritative monetary total of the receipt line.
     */
    @ColumnInfo(name = "unit_cost")
    val unitCost: Money,

    /**
     * Exact authoritative acquisition cost represented by this receipt line.
     *
     * This value is the monetary source from which the receiving workflow
     * creates cost-layer tranches.
     *
     * It is intentionally not required to equal commercial quantity multiplied
     * by unitCost exactly in currency minor units because legitimate
     * acquisition totals may be indivisible across the received quantity.
     */
    @ColumnInfo(name = "total_cost")
    val totalCost: Money,

    /**
     * Manufacturer/supplier batch or lot reference.
     *
     * Blank values are not meaningful domain values. Where no batch exists,
     * null is preferred.
     */
    @ColumnInfo(name = "batch_number")
    val batchNumber: String? = null,

    /**
     * Normalized YYYYMMDD expiry representation.
     *
     * -1 means unknown expiry or genuinely non-expiring stock.
     */
    @ColumnInfo(name = "expiry_date_int")
    val expiryDateInt: Int = StockBatch.EXPIRY_UNKNOWN_OR_NONE,

    /**
     * Physical tracking strategy for the received line.
     */
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
            "GoodsReceiptItem lineIndex must be non-negative, got: $lineIndex (id=$id)"
        }

        require(productId.isNotBlank() && productId.trim() == productId) {
            "GoodsReceiptItem productId must not be blank or contain leading/trailing whitespace"
        }

        require(receivingUnitId.isNotBlank() && receivingUnitId.trim() == receivingUnitId) {
            "GoodsReceiptItem receivingUnitId must not be blank or contain leading/trailing whitespace"
        }

        require(receivedQuantity.isPositive) {
            "GoodsReceiptItem receivedQuantity must be strictly positive, " +
                "got: ${receivedQuantity.storageUnits} (id=$id)"
        }

        require(unitCost.amountMinorUnits >= 0L) {
            "GoodsReceiptItem unitCost must not be negative, " +
                "got: ${unitCost.amountMinorUnits} (id=$id)"
        }

        require(totalCost.amountMinorUnits >= 0L) {
            "GoodsReceiptItem totalCost must not be negative, " +
                "got: ${totalCost.amountMinorUnits} (id=$id)"
        }

        require(StockBatch.isValidExpiryDateInt(expiryDateInt)) {
            "GoodsReceiptItem expiryDateInt must be -1 or a valid Gregorian " +
                "calendar date in YYYYMMDD format, got: $expiryDateInt (id=$id)"
        }

        require(
            trackingMode == StockBatch.TRACKING_STANDARD_BATCHED ||
            trackingMode == StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY ||
            trackingMode == StockBatch.TRACKING_SUPPLIER_UNTRACKED ||
            trackingMode == StockBatch.TRACKING_NON_BATCHED_COMMODITY
        ) {
            "Invalid GoodsReceiptItem trackingMode: '$trackingMode' (id=$id)"
        }

        val normalizedBatchNumber = batchNumber?.trim()

        require(batchNumber == null || batchNumber == normalizedBatchNumber) {
            "GoodsReceiptItem batchNumber must not contain leading/trailing whitespace (id=$id)"
        }

        when (trackingMode) {
            StockBatch.TRACKING_STANDARD_BATCHED -> {
                require(!normalizedBatchNumber.isNullOrBlank()) {
                    "STANDARD_BATCHED item requires a non-blank batchNumber (id=$id)"
                }

                require(expiryDateInt != StockBatch.EXPIRY_UNKNOWN_OR_NONE) {
                    "STANDARD_BATCHED item requires a known expiry date, " +
                        "not -1 (id=$id)"
                }
            }

            StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY -> {
                require(!normalizedBatchNumber.isNullOrBlank()) {
                    "BATCH_UNKNOWN_EXPIRY item requires a non-blank batchNumber (id=$id)"
                }

                require(expiryDateInt == StockBatch.EXPIRY_UNKNOWN_OR_NONE) {
                    "BATCH_UNKNOWN_EXPIRY item must have expiryDateInt = -1, " +
                        "got: $expiryDateInt (id=$id)"
                }
            }

            StockBatch.TRACKING_SUPPLIER_UNTRACKED -> {
                /*
                 * No batch identity is required at this boundary.
                 *
                 * The receiving workflow may generate a deterministic
                 * internal physical identity when the supplier provides
                 * no batch number.
                 */
            }

            StockBatch.TRACKING_NON_BATCHED_COMMODITY -> {
                require(expiryDateInt == StockBatch.EXPIRY_UNKNOWN_OR_NONE) {
                    "NON_BATCHED_COMMODITY item must have expiryDateInt = -1, " +
                        "got: $expiryDateInt (id=$id)"
                }
            }
        }

        require(createdAt > 0L) {
            "GoodsReceiptItem createdAt must be a positive epoch timestamp, " +
                "got: $createdAt (id=$id)"
        }

        require(updatedAt > 0L) {
            "GoodsReceiptItem updatedAt must be a positive epoch timestamp, " +
                "got: $updatedAt (id=$id)"
        }

        require(updatedAt >= createdAt) {
            "GoodsReceiptItem updatedAt ($updatedAt) must not precede " +
                "createdAt ($createdAt) (id=$id)"
        }
    }
}
