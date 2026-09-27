package org.mwangaza.app.scanner

/**
 * Small, embedded, general-purpose product knowledge used by the offline scanner.
 *
 * This is deliberately not a global product catalogue. It contains reusable terminology
 * and interpretation clues only. Raw scanner evidence is never replaced by these values.
 */
enum class ProductKnowledgeCategory { MEDICINE, MEDICAL_CONSUMABLE, DIAGNOSTIC, OTHER }

enum class KnowledgeConceptType {
    PRODUCT_TYPE, DOSAGE_FORM, ROUTE, SUBSTANCE, UNIT, PACKAGING, TERMINOLOGY, OCR_CORRECTION
}

data class KnowledgeConcept(
    val id: String,
    val type: KnowledgeConceptType,
    val canonical: String,
    val aliases: Set<String>,
    val categories: Set<ProductKnowledgeCategory>,
    val attributes: Map<String, String> = emptyMap()
)

interface OfflineProductKnowledge {
    val knowledgeVersion: String
    val schemaVersion: String
    fun concepts(type: KnowledgeConceptType): List<KnowledgeConcept>
    fun normalize(text: String): String?
    fun conceptsFor(text: String, type: KnowledgeConceptType? = null): List<KnowledgeConcept>
}

object EmbeddedOfflineProductKnowledge : OfflineProductKnowledge {
    override val knowledgeVersion = "phase2-1"
    override val schemaVersion = "1"

    private fun c(id: String, type: KnowledgeConceptType, canonical: String, aliases: Set<String>, category: ProductKnowledgeCategory? = null) =
        KnowledgeConcept(id, type, canonical, aliases, category?.let { setOf(it) } ?: emptySet())

    private val all = listOf(
        c("amoxicillin", KnowledgeConceptType.SUBSTANCE, "AMOXICILLIN", setOf("amoxicillin"), ProductKnowledgeCategory.MEDICINE),
        c("paracetamol", KnowledgeConceptType.SUBSTANCE, "PARACETAMOL", setOf("paracetamol","acetaminophen"), ProductKnowledgeCategory.MEDICINE),
        c("tablet", KnowledgeConceptType.DOSAGE_FORM, "TABLET", setOf("tablet","tablets","tab","tabs"), ProductKnowledgeCategory.MEDICINE),
        c("capsule", KnowledgeConceptType.DOSAGE_FORM, "CAPSULE", setOf("capsule","capsules","cap","caps"), ProductKnowledgeCategory.MEDICINE),
        c("syrup", KnowledgeConceptType.DOSAGE_FORM, "SYRUP", setOf("syrup"), ProductKnowledgeCategory.MEDICINE),
        c("suspension", KnowledgeConceptType.DOSAGE_FORM, "SUSPENSION", setOf("suspension","susp"), ProductKnowledgeCategory.MEDICINE),
        c("solution", KnowledgeConceptType.DOSAGE_FORM, "SOLUTION", setOf("solution","soln"), ProductKnowledgeCategory.MEDICINE),
        c("cream", KnowledgeConceptType.DOSAGE_FORM, "CREAM", setOf("cream"), ProductKnowledgeCategory.MEDICINE),
        c("ointment", KnowledgeConceptType.DOSAGE_FORM, "OINTMENT", setOf("ointment","oint"), ProductKnowledgeCategory.MEDICINE),
        c("gel", KnowledgeConceptType.DOSAGE_FORM, "GEL", setOf("gel"), ProductKnowledgeCategory.MEDICINE),
        c("injection", KnowledgeConceptType.DOSAGE_FORM, "INJECTION", setOf("injection","injectable","inj"), ProductKnowledgeCategory.MEDICINE),
        c("drops", KnowledgeConceptType.DOSAGE_FORM, "DROPS", setOf("drops","drop"), ProductKnowledgeCategory.MEDICINE),
        c("suppository", KnowledgeConceptType.DOSAGE_FORM, "SUPPOSITORY", setOf("suppository","suppositories"), ProductKnowledgeCategory.MEDICINE),
        c("powder", KnowledgeConceptType.DOSAGE_FORM, "POWDER", setOf("powder","powd"), ProductKnowledgeCategory.MEDICINE),
        c("oral", KnowledgeConceptType.ROUTE, "ORAL", setOf("oral","po"), ProductKnowledgeCategory.MEDICINE),
        c("topical", KnowledgeConceptType.ROUTE, "TOPICAL", setOf("topical"), ProductKnowledgeCategory.MEDICINE),
        c("intravenous", KnowledgeConceptType.ROUTE, "INTRAVENOUS", setOf("intravenous","iv"), ProductKnowledgeCategory.MEDICINE),
        c("intramuscular", KnowledgeConceptType.ROUTE, "INTRAMUSCULAR", setOf("intramuscular","im"), ProductKnowledgeCategory.MEDICINE),
        c("subcutaneous", KnowledgeConceptType.ROUTE, "SUBCUTANEOUS", setOf("subcutaneous","sc"), ProductKnowledgeCategory.MEDICINE),
        c("ophthalmic", KnowledgeConceptType.ROUTE, "OPHTHALMIC", setOf("ophthalmic","eye","ocular"), ProductKnowledgeCategory.MEDICINE),
        c("syringe", KnowledgeConceptType.PRODUCT_TYPE, "SYRINGE", setOf("syringe","syringes","syrmge"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("needle", KnowledgeConceptType.PRODUCT_TYPE, "NEEDLE", setOf("needle","needles"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("glove", KnowledgeConceptType.PRODUCT_TYPE, "GLOVE", setOf("glove","gloves"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("cannula", KnowledgeConceptType.PRODUCT_TYPE, "CANNULA", setOf("cannula","cannulas","cannulae"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("catheter", KnowledgeConceptType.PRODUCT_TYPE, "CATHETER", setOf("catheter","catheters"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("gauze", KnowledgeConceptType.PRODUCT_TYPE, "GAUZE", setOf("gauze"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("bandage", KnowledgeConceptType.PRODUCT_TYPE, "BANDAGE", setOf("bandage","bandages"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("dressing", KnowledgeConceptType.PRODUCT_TYPE, "DRESSING", setOf("dressing","dressings"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("specimen-container", KnowledgeConceptType.PRODUCT_TYPE, "SPECIMEN_CONTAINER", setOf("specimen container","sample container","urine container"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("infusion-set", KnowledgeConceptType.PRODUCT_TYPE, "INFUSION_SET", setOf("infusion set","giving set","iv set"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("sterile", KnowledgeConceptType.TERMINOLOGY, "STERILE", setOf("sterile","sterility"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("single-use", KnowledgeConceptType.TERMINOLOGY, "SINGLE_USE", setOf("single use","single-use","disposable"), ProductKnowledgeCategory.MEDICAL_CONSUMABLE),
        c("pregnancy-test", KnowledgeConceptType.PRODUCT_TYPE, "PREGNANCY_TEST", setOf("pregnancy test","pregnancy strip","hcg test"), ProductKnowledgeCategory.DIAGNOSTIC),
        c("malaria-test", KnowledgeConceptType.PRODUCT_TYPE, "MALARIA_TEST", setOf("malaria test","malaria rapid test","malaria rdt"), ProductKnowledgeCategory.DIAGNOSTIC),
        c("urinalysis-strip", KnowledgeConceptType.PRODUCT_TYPE, "URINALYSIS_STRIP", setOf("urinalysis strip","urine strip","urine reagent strip"), ProductKnowledgeCategory.DIAGNOSTIC),
        c("glucose-strip", KnowledgeConceptType.PRODUCT_TYPE, "GLUCOSE_STRIP", setOf("glucose strip","blood glucose strip","glucometer strip"), ProductKnowledgeCategory.DIAGNOSTIC),
        c("rapid-test", KnowledgeConceptType.PRODUCT_TYPE, "RAPID_TEST", setOf("rapid test","rapid diagnostic test","rdt"), ProductKnowledgeCategory.DIAGNOSTIC),
        c("test-cassette", KnowledgeConceptType.PACKAGING, "TEST_CASSETTE", setOf("test cassette","cassette test"), ProductKnowledgeCategory.DIAGNOSTIC),
        c("reagent", KnowledgeConceptType.PRODUCT_TYPE, "REAGENT", setOf("reagent","reagents"), ProductKnowledgeCategory.DIAGNOSTIC),
        c("milligram", KnowledgeConceptType.UNIT, "MG", setOf("mg","mgs"), ProductKnowledgeCategory.MEDICINE),
        c("microgram", KnowledgeConceptType.UNIT, "MCG", setOf("mcg","µg","ug"), ProductKnowledgeCategory.MEDICINE),
        c("gram", KnowledgeConceptType.UNIT, "G", setOf("g","gm","gms"), ProductKnowledgeCategory.MEDICINE),
        c("millilitre", KnowledgeConceptType.UNIT, "ML", setOf("ml","mL","millilitre","milliliter"), ProductKnowledgeCategory.MEDICINE),
        c("percent", KnowledgeConceptType.UNIT, "%", setOf("%","percent","percentage"), ProductKnowledgeCategory.MEDICINE),
        c("ocr-tablet", KnowledgeConceptType.OCR_CORRECTION, "TABLET", setOf("tabiet","tabiets"), ProductKnowledgeCategory.MEDICINE),
        c("ocr-capsule", KnowledgeConceptType.OCR_CORRECTION, "CAPSULE", setOf("capsuie","capsuies"), ProductKnowledgeCategory.MEDICINE)
    )

    override fun concepts(type: KnowledgeConceptType) = all.filter { it.type == type }

    private fun key(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")

    override fun normalize(text: String): String? {
        val k = key(text)
        return all.firstOrNull { it.aliases.any { a -> key(a) == k } }?.canonical
    }

    override fun conceptsFor(text: String, type: KnowledgeConceptType?): List<KnowledgeConcept> {
        val k = key(text)
        return all.filter { concept ->
            (type == null || concept.type == type) &&
                concept.aliases.any { key(it).let { alias -> k.contains(alias) } }
        }
    }
}
