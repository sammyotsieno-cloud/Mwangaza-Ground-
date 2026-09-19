package core.domain.model

/**
 * Stable semantic product types used to constrain identity interpretation.
 * ProductCategory remains the persisted hierarchical classification tree.
 */
enum class ProductType(
    val displayName: String
) {
    MEDICINE("Medicine"),
    MEDICAL_CONSUMABLE("Medical Consumable"),
    DIAGNOSTIC("Diagnostic"),
    WOUND_CARE("Wound Care"),
    ANTISEPTIC_DISINFECTANT("Antiseptic / Disinfectant"),
    PERSONAL_CARE_HYGIENE("Personal Care / Hygiene"),
    MEDICAL_DEVICE_EQUIPMENT("Medical Device / Equipment"),
    LABORATORY_SPECIMEN_SUPPLY("Laboratory / Specimen Supply"),
    NUTRITION_THERAPEUTIC_FOOD("Nutrition / Therapeutic Food"),
    OTHER_HEALTH_COMMODITY("Other Health Commodity")
}
