package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing the authoritative, append-only physical inventory movement ledger.
 *
 * Physical Ledger Authority & Scope:
 * - Answers exclusively: "What physical stock moved, when, why, and how much?"
 * - [StockMovement] is the SINGLE SOURCE OF TRUTH for physical inventory balances.
 * - Current physical stock for a product is deterministically derived by summing the signed
 *   [Quantity.storageUnits] of all historical movements for that product.
 * - Neither [StockBatch] nor [InventoryCostLayer] stores authoritative physical stock balances.
 * - Historical immutability: Records are append-only. Committed movements are NEVER updated or deleted.
 *   Discrepancies and reversals are corrected exclusively via compensating ledger entries.
 *
 * Separation from Accounting & Cost Allocation:
 * - [StockMovement] is strictly a PHYSICAL movement ledger, NOT an accounting journal or cost-allocation object.
 * - It does NOT answer: "Which acquisition cost layer supplied this stock?" or "What was the COGS of this movement?"
 * - Financial valuation and cost-layer depletion belong strictly to future [StockAllocation] and [InventoryCostLayer].
 * - [quantity] represents physical units of stock ([Quantity]), NEVER monetary amounts or currency.
 * - Contains zero COGS calculations, zero financial debit/credit fields, and zero cost-layer references.
 *
 * Quantity Direction Convention (Strictly Enforced):
 * - POSITIVE quantity ([Quantity.isPositive]): Physical stock ENTERS inventory.
 *     * [TYPE_PURCHASE_RECEIPT]: Inflow from purchase receipt.
 *     * [TYPE_RETURN]: Inflow from customer return.
 * - NEGATIVE quantity ([Quantity.isNegative]): Physical stock LEAVES inventory.
 *     * [TYPE_SALE]: Outflow from customer sale/dispense.
 *     * [TYPE_DAMAGE]: Outflow due to damaged goods.
 *     * [TYPE_EXPIRY]: Outflow due to expired stock quarantine/removal.
 *     * [TYPE_LOSS]: Outflow due to shrinkage, theft, or unexplained discrepancy.
 *     * [TYPE_SUPPLIER_RETURN]: Outflow returned back to vendor.
 * - NON-ZERO quantity ([!Quantity.isZero]): [TYPE_STOCK_ADJUSTMENT] can be positive (surplus found) or
 *   negative (deficit found), but cannot be zero.
 */
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StockBatch::class,
            parentColumns = ["id"],
            childColumns = ["stock_batch_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["stock_batch_id"]),
        Index(value = ["movement_type"]),
        Index(value = ["occurred_at"]),
        Index(value = ["source_transaction_ref"])
    ]
)
data class StockMovement(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "stock_batch_id")
    val stockBatchId: String? = null,

    @ColumnInfo(name = "movement_type")
    val movementType: String,

    @Embedded(prefix = "quantity_")
    val quantity: Quantity,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    @ColumnInfo(name = "source_transaction_ref")
    val sourceTransactionRef: String? = null,

    @ColumnInfo(name = "source_transaction_type")
    val sourceTransactionType: String? = null,

    @ColumnInfo(name = "initiated_by_user_id")
    val initiatedByUserId: String? = null,

    @ColumnInfo(name = "reason")
    val reason: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "StockMovement id must not be blank or contain leading/trailing whitespace"
        }
        require(productId.isNotBlank() && productId.trim() == productId) {
            "StockMovement productId must not be blank or contain leading/trailing whitespace"
        }
        if (stockBatchId != null) {
            require(stockBatchId.isNotBlank() && stockBatchId.trim() == stockBatchId) {
                "StockMovement stockBatchId must not be blank or contain whitespace if provided"
            }
        }
        require(!quantity.isZero) {
            "StockMovement quantity must not be zero"
        }
        when (movementType) {
            TYPE_PURCHASE_RECEIPT, TYPE_RETURN -> {
                require(quantity.isPositive) {
                    "StockMovement movementType '$movementType' must have a positive quantity (inflow), got: ${quantity.storageUnits}"
                }
            }
            TYPE_SALE, TYPE_DAMAGE, TYPE_EXPIRY, TYPE_LOSS, TYPE_SUPPLIER_RETURN -> {
                require(quantity.isNegative) {
                    "StockMovement movementType '$movementType' must have a negative quantity (outflow), got: ${quantity.storageUnits}"
                }
            }
            TYPE_STOCK_ADJUSTMENT -> {
                require(!quantity.isZero) {
                    "StockMovement movementType '$movementType' must have a non-zero quantity"
                }
            }
            else -> {
                throw IllegalArgumentException("Unsupported StockMovement movementType: '$movementType'")
            }
        }
        require(occurredAt > 0L) {
            "StockMovement occurredAt must be a positive epoch timestamp, got: $occurredAt (id=$id)"
        }
        require(createdAt > 0L) {
            "StockMovement createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
    }

    companion object {
        const val TYPE_PURCHASE_RECEIPT = "PURCHASE_RECEIPT"
        const val TYPE_SALE = "SALE"
        const val TYPE_STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT"
        const val TYPE_RETURN = "RETURN"
        const val TYPE_DAMAGE = "DAMAGE"
        const val TYPE_EXPIRY = "EXPIRY"
        const val TYPE_LOSS = "LOSS"
        const val TYPE_SUPPLIER_RETURN = "SUPPLIER_RETURN"
    }
}
