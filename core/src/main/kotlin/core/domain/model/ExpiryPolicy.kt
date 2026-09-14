package core.domain.model

import java.lang.StringBuilder

/**
 * Defines expiry interpretation, normalization, and stock-exit eligibility
 * for a physical StockBatch.
 *
 * Architectural authority:
 *
 * - StockBatch stores the normalized physical expiry fact/sentinel.
 * - ExpiryPolicy interprets and normalizes expiry information for operational
 *   decisions.
 * - FefoService selects among eligible stock.
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
 * ---------------------------------------------------------------------------
 * EXPIRY INPUT FLEXIBILITY
 * ---------------------------------------------------------------------------
 *
 * Real-world product expiry information is not always printed as a complete
 * YYYY-MM-DD date.
 *
 * Common examples include:
 *
 *     2026-02-19
 *     20260219
 *     2026-02
 *     202602
 *     02/2026
 *     2026
 *
 * The application accepts the information actually available and normalizes
 * it to the YYYYMMDD representation used by StockBatch.
 *
 * Missing precision is completed conservatively using the first day of the
 * available period:
 *
 *     YYYY-MM-DD -> exact supplied date
 *     YYYYMMDD   -> exact supplied date
 *     YYYY-MM    -> YYYY-MM-01
 *     YYYYMM     -> YYYY-MM-01
 *     MM/YYYY    -> YYYY-MM-01
 *     MM-YYYY    -> YYYY-MM-01
 *     YYYY       -> YYYY-01-01
 *
 * This is a normalization convention, NOT a claim that the manufacturer
 * explicitly stated the product expires on that exact day.
 *
 * For a product labelled only "02/2026", the system therefore stores
 * 20260201 because that is the earliest representable date in the supplied
 * month.
 *
 * ---------------------------------------------------------------------------
 * IMPORTANT INPUT/DOMAIN BOUNDARY
 * ---------------------------------------------------------------------------
 *
 * The normalized result remains a full YYYYMMDD integer because StockBatch
 * currently requires either:
 *
 *     -1
 *
 * or a valid Gregorian YYYYMMDD value.
 *
 * This policy therefore makes the USER INPUT flexible without weakening
 * StockBatch's normalized persistence invariant.
 *
 * ---------------------------------------------------------------------------
 * UNKNOWN VS NON-EXPIRING
 * ---------------------------------------------------------------------------
 *
 * StockBatch currently uses -1 for both:
 *
 * - unknown expiry;
 * - genuinely non-expiring stock.
 *
 * ExpiryPolicy deliberately does NOT treat every -1 as non-expiring.
 *
 * Only NON_BATCHED_COMMODITY explicitly identifies the current representation
 * as non-expiring.
 *
 * Other -1 cases remain UNKNOWN.
 */
object ExpiryPolicy {

    enum class Status {
        VALID,
        EXPIRING_TODAY,
        EXPIRED,
        UNKNOWN,
        NON_EXPIRING
    }

    enum class Eligibility {
        ELIGIBLE,
        NOT_ELIGIBLE
    }

    /**
     * Normalizes flexible user-entered expiry information into the exact
     * YYYYMMDD representation required by StockBatch.
     *
     * Supported examples:
     *
     *     20260219 -> 20260219
     *     2026-02-19 -> 20260219
     *     2026/02/19 -> 20260219
     *     2026.02.19 -> 20260219
     *
     *     202602 -> 20260201
     *     2026-02 -> 20260201
     *     2026/02 -> 20260201
     *     02/2026 -> 20260201
     *     02-2026 -> 20260201
     *
     *     2026 -> 20260101
     *
     * Missing day/month precision is therefore represented by the first day
     * of the available period.
     *
     * An empty value is treated as unknown and returns
     * StockBatch.EXPIRY_UNKNOWN_OR_NONE.
     *
     * Invalid calendar dates are rejected rather than silently corrected.
     */
    fun normalizeExpiryInput(
        input: String?
    ): Int {

        if (input == null) {
            return StockBatch.EXPIRY_UNKNOWN_OR_NONE
        }

        val normalized = input.trim()

        if (normalized.isEmpty()) {
            return StockBatch.EXPIRY_UNKNOWN_OR_NONE
        }

        if (normalized == StockBatch.EXPIRY_UNKNOWN_OR_NONE.toString()) {
            return StockBatch.EXPIRY_UNKNOWN_OR_NONE
        }

        val cleaned = normalized
            .replace('/', '-')
            .replace('.', '-')
            .replace(Regex("\\s+"), "")

        /*
         * Full compact date:
         *
         * 20260219
         */
        if (cleaned.matches(Regex("^\\d{8}$"))) {
            return parseAndEncode(
                year = cleaned.substring(0, 4).toInt(),
                month = cleaned.substring(4, 6).toInt(),
                day = cleaned.substring(6, 8).toInt()
            )
        }

        /*
         * Compact year + month:
         *
         * 202602
         */
        if (cleaned.matches(Regex("^\\d{6}$"))) {
            return parseAndEncode(
                year = cleaned.substring(0, 4).toInt(),
                month = cleaned.substring(4, 6).toInt(),
                day = 1
            )
        }

        /*
         * Year only:
         *
         * 2026
         *
         * Missing month and day are represented by January 1.
         */
        if (cleaned.matches(Regex("^\\d{4}$"))) {
            return parseAndEncode(
                year = cleaned.toInt(),
                month = 1,
                day = 1
            )
        }

        /*
         * Full separated date:
         *
         * 2026-02-19
         */
        val fullDate =
            Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
                .matchEntire(cleaned)

        if (fullDate != null) {
            return parseAndEncode(
                year = fullDate.groupValues[1].toInt(),
                month = fullDate.groupValues[2].toInt(),
                day = fullDate.groupValues[3].toInt()
            )
        }

        /*
         * Year-month:
         *
         * 2026-02
         */
        val yearMonth =
            Regex("^(\\d{4})-(\\d{1,2})$")
                .matchEntire(cleaned)

        if (yearMonth != null) {
            return parseAndEncode(
                year = yearMonth.groupValues[1].toInt(),
                month = yearMonth.groupValues[2].toInt(),
                day = 1
            )
        }

        /*
         * Month-year:
         *
         * 02-2026
         *
         * This is useful because physical packaging frequently presents
         * expiry as MM/YYYY.
         */
        val monthYear =
            Regex("^(\\d{1,2})-(\\d{4})$")
                .matchEntire(cleaned)

        if (monthYear != null) {
            return parseAndEncode(
                year = monthYear.groupValues[2].toInt(),
                month = monthYear.groupValues[1].toInt(),
                day = 1
            )
        }

        throw IllegalArgumentException(
            "Unsupported expiry date format: '$input'. " +
                "Use a full date such as 20260219 or 2026-02-19, " +
                "a month/year such as 202602 or 02/2026, " +
                "or a year such as 2026."
        )
    }

    /**
     * Evaluates the expiry status of [batch] against [today].
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
             * NON_BATCHED_COMMODITY explicitly identifies the -1 sentinel
             * as non-expiring stock.
             */
            batch.trackingMode ==
                StockBatch.TRACKING_NON_BATCHED_COMMODITY &&
                batch.expiryDateInt ==
                StockBatch.EXPIRY_UNKNOWN_OR_NONE -> {

                Status.NON_EXPIRING
            }

            /*
             * Explicitly unknown expiry.
             */
            batch.trackingMode ==
                StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY -> {

                Status.UNKNOWN
            }

            /*
             * STANDARD_BATCHED and SUPPLIER_UNTRACKED may legitimately
             * contain -1, but the current schema cannot distinguish unknown
             * expiry from genuinely non-expiring stock.
             *
             * Therefore remain conservative.
             */
            batch.expiryDateInt ==
                StockBatch.EXPIRY_UNKNOWN_OR_NONE -> {

                Status.UNKNOWN
            }

            else -> {

                val expiryDate =
                    StockBatch.parseExpiryDateInt(
                        batch.expiryDateInt
                    ) ?: return Status.UNKNOWN

                when {
                    expiryDate < today ->
                        Status.EXPIRED

                    expiryDate == today ->
                        Status.EXPIRING_TODAY

                    else ->
                        Status.VALID
                }
            }
        }
    }

    /**
     * Determines whether a batch may participate in stock-exit selection.
     *
     * Policy:
     *
     * VALID            -> eligible
     * EXPIRING_TODAY   -> eligible
     * EXPIRED          -> not eligible
     * UNKNOWN          -> not eligible
     * NON_EXPIRING     -> eligible
     */
    fun eligibility(
        batch: StockBatch,
        today: LocalDateValue
    ): Eligibility =
        when (status(batch, today)) {

            Status.VALID,
            Status.EXPIRING_TODAY,
            Status.NON_EXPIRING ->
                Eligibility.ELIGIBLE

            Status.EXPIRED,
            Status.UNKNOWN ->
                Eligibility.NOT_ELIGIBLE
        }

    fun isEligibleForStockExit(
        batch: StockBatch,
        today: LocalDateValue
    ): Boolean =
        eligibility(batch, today) == Eligibility.ELIGIBLE

    fun isExpired(
        batch: StockBatch,
        today: LocalDateValue
    ): Boolean =
        status(batch, today) == Status.EXPIRED

    fun expiresToday(
        batch: StockBatch,
        today: LocalDateValue
    ): Boolean =
        status(batch, today) == Status.EXPIRING_TODAY

    fun isNonExpiring(
        batch: StockBatch,
        today: LocalDateValue
    ): Boolean =
        status(batch, today) == Status.NON_EXPIRING

    fun isUnknown(
        batch: StockBatch,
        today: LocalDateValue
    ): Boolean =
        status(batch, today) == Status.UNKNOWN

    /**
     * Creates and validates a LocalDateValue from the flexible expiry input.
     *
     * This is useful when a workflow needs the normalized date itself rather
     * than only the StockBatch YYYYMMDD representation.
     *
     * Unknown/empty input returns null.
     */
    fun parseExpiryInput(
        input: String?
    ): LocalDateValue? {

        val normalized =
            normalizeExpiryInput(input)

        if (normalized ==
            StockBatch.EXPIRY_UNKNOWN_OR_NONE
        ) {
            return null
        }

        return StockBatch.parseExpiryDateInt(normalized)
    }

    /**
     * Converts a normalized LocalDateValue into the StockBatch YYYYMMDD
     * representation.
     */
    fun toExpiryDateInt(
        date: LocalDateValue
    ): Int =
        StockBatch.toExpiryDateInt(date)

    private fun parseAndEncode(
        year: Int,
        month: Int,
        day: Int
    ): Int {

        val date =
            LocalDateValue(
                year = year,
                month = month,
                day = day
            )

        return StockBatch.toExpiryDateInt(date)
    }
}
