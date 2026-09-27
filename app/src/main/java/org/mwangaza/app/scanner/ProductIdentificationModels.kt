package org.mwangaza.app.scanner

import core.domain.model.ProductMaster

enum class ObservationSource { OCR, BARCODE, VISUAL, USER, DERIVED }

enum class ObservationType { TEXT, BARCODE, PRODUCT_CATEGORY_CLUE, PRODUCT_TYPE_CLUE, ATTRIBUTE }

data class ProductObservation(
    val rawValue: String,
    val normalizedValue: String? = null,
    val type: ObservationType,
    val source: ObservationSource,
    val confidence: Float? = null,
    val sourceImageUri: String? = null
) {
    init {
        require(rawValue.isNotBlank())
        require(confidence == null || confidence in 0f..1f)
    }
}

enum class AttributeRequirement { REQUIRED, OPTIONAL, NOT_APPLICABLE }

data class ProductAttributeRule(val attribute: String, val requirement: AttributeRequirement)

data class ProductAttributeSchema(
    val category: ProductKnowledgeCategory,
    val rules: List<ProductAttributeRule>
) {
    fun requirement(attribute: String) =
        rules.firstOrNull { it.attribute == attribute }?.requirement
            ?: AttributeRequirement.NOT_APPLICABLE
}

object ProductAttributeSchemas {
    val medicine = ProductAttributeSchema(
        ProductKnowledgeCategory.MEDICINE,
        listOf(
            ProductAttributeRule("genericName", AttributeRequirement.REQUIRED),
            ProductAttributeRule("strength", AttributeRequirement.REQUIRED),
            ProductAttributeRule("dosageForm", AttributeRequirement.REQUIRED),
            ProductAttributeRule("route", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("manufacturer", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("brandName", AttributeRequirement.OPTIONAL)
        )
    )

    val medicalConsumable = ProductAttributeSchema(
        ProductKnowledgeCategory.MEDICAL_CONSUMABLE,
        listOf(
            ProductAttributeRule("productType", AttributeRequirement.REQUIRED),
            ProductAttributeRule("material", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("size", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("dimensions", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("capacity", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("sterility", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("packQuantity", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("manufacturer", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("brandName", AttributeRequirement.OPTIONAL)
        )
    )

    val diagnostic = ProductAttributeSchema(
        ProductKnowledgeCategory.DIAGNOSTIC,
        listOf(
            ProductAttributeRule("productType", AttributeRequirement.REQUIRED),
            ProductAttributeRule("testType", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("specimen", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("testFormat", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("packQuantity", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("manufacturer", AttributeRequirement.OPTIONAL),
            ProductAttributeRule("brandName", AttributeRequirement.OPTIONAL)
        )
    )

    fun forCategory(category: ProductKnowledgeCategory) = when (category) {
        ProductKnowledgeCategory.MEDICINE -> medicine
        ProductKnowledgeCategory.MEDICAL_CONSUMABLE -> medicalConsumable
        ProductKnowledgeCategory.DIAGNOSTIC -> diagnostic
        ProductKnowledgeCategory.OTHER -> ProductAttributeSchema(category, emptyList())
    }
}

enum class IdentificationState {
    IDENTIFIED, AMBIGUOUS, UNKNOWN, CONFLICT, NEEDS_MORE_EVIDENCE
}

data class CategoryClassification(
    val category: ProductKnowledgeCategory,
    val confidence: Float,
    val supportingObservations: List<ProductObservation>,
    val explanation: String
)

data class IdentificationCandidate(
    val category: ProductKnowledgeCategory,
    val attributes: Map<String, String>,
    val supportingObservations: List<ProductObservation>,
    val contradictions: List<String> = emptyList()
)

data class ProductMatch(
    val product: ProductMaster,
    val matchedAttributes: Map<String, String>,
    val unmatchedCandidateAttributes: Set<String>,
    val evidence: List<String>
)

data class ProductIdentificationResult(
    val state: IdentificationState,
    val category: CategoryClassification,
    val candidate: IdentificationCandidate?,
    val matches: List<ProductMatch> = emptyList(),
    val observations: List<ProductObservation> = emptyList(),
    val contradictions: List<String> = emptyList(),
    val missingRequiredAttributes: Set<String> = emptySet(),
    val explanation: String,
    val knowledgeVersion: String,
    val schemaVersion: String
)
