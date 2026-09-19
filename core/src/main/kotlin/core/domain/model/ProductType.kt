package core.domain.model

/**
 * Stable semantic product types used to constrain identity interpretation.
 * ProductCategory remains the persisted hierarchical classification tree.
 */
enum class ProductType(
    val keycode: String,
    val displayName: String
) {
    MEDICINE("MED", "Medicine"),
    MEDICAL_CONSUMABLE("CONS", "Medical Consumable"),
    DIAGNOSTIC("DIAG", "Diagnostic"),
    WOUND_CARE("WOUND", "Wound Care"),
    ANTISEPTIC_DISINFECTANT("ANTI", "Antiseptic / Disinfectant"),
    PERSONAL_CARE_HYGIENE("HYGI", "Personal Care / Hygiene"),
    MEDICAL_DEVICE_EQUIPMENT("DEVICE", "Medical Device / Equipment"),
    LABORATORY_SPECIMEN_SUPPLY("LAB", "Laboratory / Specimen Supply"),
    NUTRITION_THERAPEUTIC_FOOD("NUTR", "Nutrition / Therapeutic Food"),
    OTHER_HEALTH_COMMODITY("OTHER", "Other Health Commodity");

    companion object {
        fun fromKeycode(value: String?): ProductType? =
            entries.firstOrNull { it.keycode.equals(value?.trim(), ignoreCase = true) }
    }
}
