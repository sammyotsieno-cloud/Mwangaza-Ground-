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
 * Architectural authority:
 * - StockBatch answers: "Which physical lot is this stock from?"
 * - InventoryCostLayer answers: "At what acquisition cost was this stock acquired?"
 * - StockMovement answers: "What physical stock-flow event changed quantity?"
 *
 * StockBatch therefore MUST NOT contain:
 * - acquisition cost;
 * - remaining stock quantity;
 * - COGS state;
 * - supplier-specific financial attribution;
 * - selling price;
 * - cost-layer references.
 *
 * A physical batch may be received multiple times. Re-receiving the same physical
 * batch reuses this StockBatch identity while creating a separate InventoryCostLayer
 * for the new acquisition event.
 *
 * Physical identity:
 * - productId
 * - normalized batchNumber
 * - expiryDateInt
 *
 * The database uniqueness constraint protects this physical identity:
 * UNIQUE(productId, batchNumber, expiryDateInt)
 *
 * Expiry:
 * - -1 means unknown expiry or genuinely non-expiring stock.
 * - Otherwise expiryDateInt must be a valid Gregorian YYYYMMDD date.
 *
 * Tracking modes describe the physical traceability condition. They do not
 * represent financial state.
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
        Index(
            value = ["product_id", "batch_number", "expiry_date_int"],
            unique = true
        ),
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

    /**
     * Physical lot identifier.
     *
     * The value is normalized at the domain boundary:
     * - non-blank;
     * - no leading/trailing whitespace;
     * - uppercase.
     *
     * For SUPPLIER_UNTRACKED stock this must still be a generated,
     * receipt-isolated physical identifier. StockBatch itself does not
     * generate that identifier.
     */
    @ColumnInfo(name = "batch_number")
    val batchNumber: String,

    /**
     * Physical expiry state:
     * - -1 = unknown expiry or genuinely non-expiring;
     * - otherwise valid Gregorian YYYYMMDD.
     */
    @ColumnInfo(name = "expiry_date_int")
    val expiryDateInt: Int,

    /**
     * Describes physical traceability only.
     *
     * It must never be interpreted as acquisition-cost state.
     */
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

        require(
            batchNumber.isNotBlank() &&
                batchNumber.trim() == batchNumber &&
                batchNumber == batchNumber.uppercase()
        ) {
            "StockBatch batchNumber must be non-blank, trimmed, and uppercase, got: '$batchNumber'"
        }

        require(isValidExpiryDateInt(expiryDateInt)) {
            "StockBatch expiryDateInt must be -1 or a valid Gregorian " +
                "calendar date in YYYYMMDD format, got: $expiryDateInt"
        }

        require(
            trackingMode == TRACKING_STANDARD_BATCHED ||
                trackingMode == TRACKING_BATCH_UNKNOWN_EXPIRY ||
                trackingMode == TRACKING_SUPPLIER_UNTRACKED ||
                trackingMode == TRACKING_NON_BATCHED_COMMODITY
        ) {
            "Invalid StockBatch trackingMode: '$trackingMode'"
        }

        /*
         * Tracking-mode/expiry combinations are physical semantics, not
         * financial semantics.
         *
         * STANDARD_BATCHED:
         *   A known physical batch may have a known expiry or unknown expiry.
         *
         * BATCH_UNKNOWN_EXPIRY:
         *   Explicitly represents a batch whose expiry is unknown.
         *
         * SUPPLIER_UNTRACKED:
         *   The receiving workflow must have generated an isolated physical
         *   identifier. StockBatch does not generate it itself.
         *
         * NON_BATCHED_COMMODITY:
         *   Must not carry an expiry because the model explicitly represents
         *   non-batched commodity stock.
         */
        when (trackingMode) {
            TRACKING_BATCH_UNKNOWN_EXPIRY -> {
                require(expiryDateInt == EXPIRY_UNKNOWN_OR_NONE) {
                    "BATCH_UNKNOWN_EXPIRY must use EXPIRY_UNKNOWN_OR_NONE (-1), " +
                        "got: $expiryDateInt"
                }
            }

            TRACKING_NON_BATCHED_COMMODITY -> {
                require(expiryDateInt == EXPIRY_UNKNOWN_OR_NONE) {
                    "NON_BATCHED_COMMODITY must use EXPIRY_UNKNOWN_OR_NONE (-1), " +
                        "got: $expiryDateInt"
                }
            }

            TRACKING_SUPPLIER_UNTRACKED,
            TRACKING_STANDARD_BATCHED -> {
                // These modes may legitimately carry either a known expiry
                // or the -1 unknown/non-expiring sentinel.
            }
        }

        require(createdAt > 0L) {
            "StockBatch createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }

        require(updatedAt > 0L) {
            "StockBatch updatedAt must be a positive epoch timestamp, got: $updatedAt (id=$id)"
        }

        require(updatedAt >= createdAt) {
            "StockBatch updatedAt ($updatedAt) must not precede " +
                "createdAt ($createdAt) (id=$id)"
        }
    }

    companion object {

        /**
         * Sentinel representing:
         * - unknown expiry; OR
         * - genuinely non-expiring stock.
         *
         * ExpiryPolicy remains responsible for deciding how each case
         * participates in stock-exit eligibility.
         */
        const val EXPIRY_UNKNOWN_OR_NONE: Int = -1

        /**
         * Physical lot with normal batch traceability.
         */
        const val TRACKING_STANDARD_BATCHED = "STANDARD_BATCHED"

        /**
         * Physical batch exists but expiry information is unavailable.
         */
        const val TRACKING_BATCH_UNKNOWN_EXPIRY = "BATCH_UNKNOWN_EXPIRY"

        /**
         * Supplier did not provide a manufacturer batch identifier.
         *
         * The receiving workflow must generate an isolated physical
         * identifier before constructing StockBatch.
         */
        const val TRACKING_SUPPLIER_UNTRACKED = "SUPPLIER_UNTRACKED"

        /**
         * Product is physically treated as non-batched commodity stock.
         */
        const val TRACKING_NON_BATCHED_COMMODITY = "NON_BATCHED_COMMODITY"

        /**
         * Validates whether [expiryDateInt] represents either
         * [EXPIRY_UNKNOWN_OR_NONE] (-1) or a strictly valid Gregorian
         * calendar date in YYYYMMDD format.
         */
        fun isValidExpiryDateInt(expiryDateInt: Int): Boolean {
            if (expiryDateInt == EXPIRY_UNKNOWN_OR_NONE) return true

            if (expiryDateInt < 19000101 || expiryDateInt > 30001231) {
                return false
            }

            val year = expiryDateInt / 10000
            val month = (expiryDateInt % 10000) / 100
            val day = expiryDateInt % 100

            return try {
                LocalDateValue(
                    year = year,
                    month = month,
                    day = day
                )
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }

        /**
         * Converts a validated LocalDateValue into YYYYMMDD.
         */
        fun toExpiryDateInt(date: LocalDateValue): Int =
            date.year * 10000 +
                date.month * 100 +
                date.day

        /**
         * Reconstructs a LocalDateValue from YYYYMMDD.
         *
         * Returns null for the -1 sentinel.
         */
        fun parseExpiryDateInt(
            expiryDateInt: Int
        ): LocalDateValue? {

            if (expiryDateInt == EXPIRY_UNKNOWN_OR_NONE) {
                return null
            }

            require(isValidExpiryDateInt(expiryDateInt)) {
                "Invalid Gregorian calendar expiry date integer: $expiryDateInt"
            }

            val year = expiryDateInt / 10000
            val month = (expiryDateInt % 10000) / 100
            val day = expiryDateInt % 100

            return LocalDateValue(
                year = year,
                month = month,
                day = day
            )
        }
    }
}
