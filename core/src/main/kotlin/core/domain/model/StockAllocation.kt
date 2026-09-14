package core.domain.model

/**
 * Defines expiry status and stock-exit eligibility for a physical StockBatch.
 *
 * Architectural authority:
 * - StockBatch stores the physical expiry fact/sentinel.
 * - ExpiryPolicy interprets that fact for operational stock-exit decisions.
 * - FefoService is responsible for selecting among eligible stock.
 *
 * ExpiryPolicy MUST NOT:
 * - mutate StockBatch;
 * - calculate stock balances;
 * - allocate cost layers;
 * - perform COGS;
 * - perform FEFO ordering itself;
 * - decide selling eligibility;
 * - silently convert unknown expiry into non-expiring stock.
 *
 * Important semantic boundary:
 *
 * StockBatch currently uses expiryDateInt = -1 for both:
 * - unknown expiry; and
 * - genuinely non-expiring stock.
 *
 * Tracking mode can explicitly identify NON_BATCHED_COMMODITY as
 * non-expiring, but STANDARD_BATCHED + -1 remains ambiguous.
 *
 * Therefore this policy deliberately treats ambiguous -1 expiry as
 * UNKNOWN rather than assuming that the stock never expires.
 */
object ExpiryPolicy {

    /**
     * Operational expiry classification.
     */
    enum class Status {
        /**
         * A concrete expiry date is known and is later than today.
         */
        VALID,

        /**
         * The known expiry date is today.
         */
        EXPIRING_TODAY,

        /**
         * The known expiry date has already passed.
         */
        EXPIRED,

        /**
         * Expiry information is unavailable or ambiguous.
         */
        UNKNOWN,

        /**
         * The stock is explicitly represented as non-expiring.
         */
        NON_EXPIRING
    }

    /**
     * Stock-exit eligibility under the expiry policy.
     *
     * UNKNOWN is deliberately not eligible by default because an unknown
     * expiry must not be silently treated as safe indefinitely.
     */
    enum class Eligibility {
        ELIGIBLE,
        NOT_ELIGIBLE
    }

    /**
     * Evaluates the expiry status of [batch] against [today].
     *
     * [today] must be a validated LocalDateValue.
     */
    fun status(
        batch: StockBatch,
        today: LocalDateValue
    ): Status {
        require(batch.id.isNotBlank()) {
            "StockBatch id must not be blank"
        }

        return when {
            /*
             * Explicitly non-batched commodity stock is the only current
             * StockBatch representation that unambiguously identifies
             * -1 as a non-expiring condition.
             */
            batch.trackingMode == StockBatch.TRACKING_NON_BATCHED_COMMODITY &&
                batch.expiryDateInt == StockBatch.EXPIRY_UNKNOWN_OR_NONE -> {
                Status.NON_EXPIRING
            }

            /*
             * Explicit unknown-expiry tracking mode.
             */
            batch.trackingMode == StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY -> {
                Status.UNKNOWN
            }

            /*
             * A standard/supplier-untracked batch with -1 is ambiguous:
             * the current StockBatch schema cannot distinguish unknown
             * expiry from genuinely non-expiring stock.
             *
             * Conservative interpretation: UNKNOWN.
             */
            batch.expiryDateInt == StockBatch.EXPIRY_UNKNOWN_OR_NONE -> {
                Status.UNKNOWN
            }

            else -> {
                val expiryDate = StockBatch.parseExpiryDateInt(
                    batch.expiryDateInt
                ) ?: return Status.UNKNOWN

                when {
                    expiryDate < today -> Status.EXPIRED
                    expiryDate == today -> Status.EXPIRING_TODAY
                    else -> Status.VALID
                }
            }
        }
    }

    /**
     * Determines whether a batch may participate in stock-exit selection.
     *
     * Policy:
     * - VALID -> eligible
     * - EXPIRING_TODAY -> eligible
     * - EXPIRED -> not eligible
     * - UNKNOWN -> not eligible
     * - NON_EXPIRING -> eligible
     *
     * Unknown expiry is intentionally conservative. A future workflow may
     * introduce an explicit business rule for unknown-expiry stock, but
     * that decision must be deliberate rather than implied by -1.
     */
    fun eligibility(
        batch: StockBatch,
        today: LocalDateValue
    ): Eligibility =
        when (status(batch, today)) {
            Status.VALID,
            Status.EXPIRING_TODAY,
            Status.NON_EXPIRING -> Eligibility.ELIGIBLE

            Status.EXPIRED,
            Status.UNKNOWN -> Eligibility.NOT_ELIGIBLE
        }

    /**
     * Convenience predicate for FEFO/stock-exit callers.
     */
    fun isEligibleForStockExit(
        batch: StockBatch,
        today: LocalDateValue
    ): Boolean =
        eligibility(batch, today) == Eligibility.ELIGIBLE

    /**
     * Convenience predicate for expiry warnings.
     */
    fun isExpired(
        batch: StockBatch,
        today: LocalDateValue
    ): Boolean =
        status(batch, today) == Status.EXPIRED

    /**
     * Convenience predicate for stock expiring on the supplied date.
     */
    fun expiresToday(
        batch: StockBatch,
        today: LocalDateValue
    ): Boolean =
        status(batch, today) == Status.EXPIRING_TODAY

    /**
     * Convenience predicate for explicitly non-expiring stock.
     */
    fun isNonExpiring(
        batch: StockBatch,
        today: LocalDateValue
    ): Boolean =
        status(batch, today) == Status.NON_EXPIRING

    /**
     * Convenience predicate for stock whose expiry cannot safely be known.
     */
    fun isUnknown(
        batch: StockBatch,
        today: LocalDateValue
    ): Boolean =
        status(batch, today) == Status.UNKNOWN
}
