package org.mwangaza.app.scanner

/**
 * Deterministic validation for numeric retail/product identifier formats already
 * supported by the scanner's barcode layer.
 *
 * This validates structural/check-digit plausibility only; it does not establish
 * that a value is actually the identifier of a particular product.
 */
internal object ProductIdentifierValidator {
    fun isValid(format: String, raw: String): Boolean = when (format) {
        "EAN_13" -> raw.length == 13 && raw.all(Char::isDigit) && validCheckDigit(raw)
        "EAN_8" -> raw.length == 8 && raw.all(Char::isDigit) && validCheckDigit(raw)
        "UPC_A" -> raw.length == 12 && raw.all(Char::isDigit) && validCheckDigit(raw)
        "UPC_E" -> validUpcE(raw)
        else -> false
    }

    fun isPlausibleOcrIdentifier(raw: String): Boolean = when (raw.length) {
        8 -> isValid("EAN_8", raw) || isValid("UPC_E", raw)
        12 -> isValid("UPC_A", raw)
        13 -> isValid("EAN_13", raw)
        else -> false
    }

    private fun validCheckDigit(raw: String): Boolean {
        if (raw.length < 2 || !raw.all(Char::isDigit)) return false
        val check = raw.last().digitToInt()
        val sum = raw.dropLast(1).map { it.digitToInt() }.reversed()
            .mapIndexed { index, digit -> digit * if (index % 2 == 0) 3 else 1 }.sum()
        return (10 - (sum % 10)) % 10 == check
    }

    private fun validUpcE(raw: String): Boolean {
        if (!raw.all(Char::isDigit) || raw.length != 8) return false
        val numberSystem = raw[0]
        val compressed = raw.substring(1, 7)
        val check = raw[7].digitToInt()
        val expanded = expandUpcE(numberSystem, compressed) ?: return false
        return validCheckDigit(expanded + check)
    }

    private fun expandUpcE(numberSystem: Char, compressed: String): String? {
        if (compressed.length != 6 || numberSystem !in '0'..'1') return null
        val d = compressed.map { it.digitToInt() }
        val body = when (d[5]) {
            0, 1, 2 -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            3 -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            4 -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        return numberSystem + body
    }
}