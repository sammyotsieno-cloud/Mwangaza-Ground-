package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import core.domain.time.LocalDateValue

/**
 * Room entity representing the physical traceability identity of a product lot or batch.
 *
 * Core Concept:
 * - Answers: "Which physical manufacturer lot or batch is this stock from, and when does it expire?"
 * - Represents physical/traceability identity ONLY.
 * - Does NOT own financial acquisition cost (belongs to [InventoryCostLayer]).
 * - Does NOT own physical stock movement or running balances (derived from [StockMovement]).
 * - Does NOT own supplier ownership: a physical batch may be received multiple times across different
 *   receipts or suppliers. Supplier attribution lives at the acquisition level ([InventoryCostLayer]).
 *
 * Uniqueness & Expiry Architecture:
 * - Unique constraint: UNIQUE([productId], [batchNumber], [expiryDateInt]).
 * - [batchNumber] is normalized: non-blank, trimmed, and uppercase.
 * - [expiryDateInt] format: integer YYYYMMDD (e.g. 20261231) representing a real Gregorian calendar date.
 * - Unknown or non-expiring stock uses sentinel value [EXPIRY_UNKNOWN_OR_NONE] (-1).
 * - Nullable expiry is strictly prohibited to prevent SQLite NULL uniqueness duplication flaws.
 * - Calendar validity is strictly enforced: impossible calendar dates (such as 20260231 or 20260431)
 *   and non-leap February 29ths are rejected via [LocalDateValue] Gregorian calendar rules.
 * - Re-receiving the same physical batch (same product, same batch number, same expiry) reuses this
 *   existing [StockBatch] record and appends a new [InventoryCostLayer].
 *
 * Supplier-Untracked Stock Contract:
 * - For stock received without a manufacturer batch number ([TRACKING_SUPPLIER_UNTRACKED]), the
 *   receiving workflow (when implemented in later purchasing phases) will generate an isolated,
 *   receipt-specific lot identifier (e.g., following an isolated receipt convention) before creating
 *   the [StockBatch].
 * - [StockBatch] does NOT invent or parse GoodsReceipt IDs or implement receiving workflows.
 * - This prevents unrelated untracked receipts of the same product from collapsing into a single lot,
 *   while preserving the (product_id, batch_number, expiry_date_int) uniqueness constraint without
 *   erroneously attaching supplier_id to physical batch identity.
 *
 * Barcode Policy:
 * - Barcode/GTIN/QR fields are strictly excluded.
 */
@Entity(
    tableName = "stock_batches",
    foreignKeys = [
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id", "batch_number", "expiry_date_int"], unique = true),
        Index(value = ["product_id"]),
        Index(value = ["expiry_date_int"])
    ]
)
data class StockBatch(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "batch_number")
    val batchNumber: String,

    @ColumnInfo(name = "expiry_date_int")
    val expiryDateInt: Int,

    @ColumnInfo(name = "tracking_mode")
    val trackingMode: String = TRACKING_STANDARD_BATCHED,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "StockBatch id must not be blank or contain leading/trailing whitespace"
        }
        require(productId.isNotBlank() && productId.trim() == productId) {
            "StockBatch productId must not be blank or contain leading/trailing whitespace"
        }
        require(batchNumber.isNotBlank() && batchNumber.trim() == batchNumber && batchNumber == batchNumber.uppercase()) {
            "StockBatch batchNumber must be non-blank, trimmed, and uppercase, got: '$batchNumber'"
        }
        require(isValidExpiryDateInt(expiryDateInt)) {
            "StockBatch expiryDateInt must be -1 or a valid Gregorian calendar date in YYYYMMDD format, got: $expiryDateInt"
        }
        require(
            trackingMode == TRACKING_STANDARD_BATCHED ||
            trackingMode == TRACKING_BATCH_UNKNOWN_EXPIRY ||
            trackingMode == TRACKING_SUPPLIER_UNTRACKED ||
            trackingMode == TRACKING_NON_BATCHED_COMMODITY
        ) {
            "Invalid StockBatch trackingMode: '$trackingMode'"
        }
        require(createdAt > 0L) {
            "StockBatch createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
        require(updatedAt > 0L) {
            "StockBatch updatedAt must be a positive epoch timestamp, got: $updatedAt (id=$id)"
        }
        require(updatedAt >= createdAt) {
            "StockBatch updatedAt ($updatedAt) must not precede createdAt ($createdAt) (id=$id)"
        }
    }

    companion object {
        const val EXPIRY_UNKNOWN_OR_NONE: Int = -1

        const val TRACKING_STANDARD_BATCHED = "STANDARD_BATCHED"
        const val TRACKING_BATCH_UNKNOWN_EXPIRY = "BATCH_UNKNOWN_EXPIRY"
        const val TRACKING_SUPPLIER_UNTRACKED = "SUPPLIER_UNTRACKED"
        const val TRACKING_NON_BATCHED_COMMODITY = "NON_BATCHED_COMMODITY"

        /**
         * Validates whether [expiryDateInt] represents either [EXPIRY_UNKNOWN_OR_NONE] (-1)
         * or a strictly valid Gregorian calendar date in YYYYMMDD format via [LocalDateValue].
         * Rejects impossible dates (e.g. 20260231, 20260431, 20251340) and invalid leap years.
         */
        fun isValidExpiryDateInt(expiryDateInt: Int): Boolean {
            if (expiryDateInt == EXPIRY_UNKNOWN_OR_NONE) return true
            if (expiryDateInt < 19000101 || expiryDateInt > 30001231) return false
            val year = expiryDateInt / 10000
            val month = (expiryDateInt % 10000) / 100
            val day = expiryDateInt % 100
            return try {
                LocalDateValue(year = year, month = month, day = day)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }

        /**
         * Converts a validated [LocalDateValue] into the YYYYMMDD integer representation.
         */
        fun toExpiryDateInt(date: LocalDateValue): Int =
            date.year * 10000 + date.month * 100 + date.day

        /**
         * Reconstructs a [LocalDateValue] from a YYYYMMDD integer representation.
         * Returns null if [expiryDateInt] is [EXPIRY_UNKNOWN_OR_NONE] (-1).
         * Throws [IllegalArgumentException] if [expiryDateInt] is not a valid Gregorian date.
         */
        fun parseExpiryDateInt(expiryDateInt: Int): LocalDateValue? {
            if (expiryDateInt == EXPIRY_UNKNOWN_OR_NONE) return null
            require(isValidExpiryDateInt(expiryDateInt)) {
                "Invalid Gregorian calendar expiry date integer: $expiryDateInt"
            }
            val year = expiryDateInt / 10000
            val month = (expiryDateInt % 10000) / 100
            val day = expiryDateInt % 100
            return LocalDateValue(year = year, month = month, day = day)
        }
    }
}
