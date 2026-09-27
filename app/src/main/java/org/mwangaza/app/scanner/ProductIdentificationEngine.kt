package org.mwangaza.app.scanner

import core.domain.model.ProductMaster
import java.util.Locale

interface FacilityProductMatcher {
    fun match(candidate: IdentificationCandidate, products: List<ProductMaster>): List<ProductMatch>
}

object ProductMasterMatcher : FacilityProductMatcher {
    override fun match(candidate: IdentificationCandidate, products: List<ProductMaster>): List<ProductMatch> {
        val schema = ProductAttributeSchemas.forCategory(candidate.category)
        return products.mapNotNull { product ->
            val relevant = candidate.attributes.filterKeys { schema.requirement(it) != AttributeRequirement.NOT_APPLICABLE }
            val matched = relevant.filter { (key, value) ->
                when (key) {
                    "brandName" -> same(value, product.brandName)
                    "genericName" -> same(value, product.genericName)
                    "manufacturer" -> same(value, product.manufacturer)
                    "productType" -> same(value, product.productType)
                    else -> false
                }
            }
            if (matched.isEmpty()) null else ProductMatch(product, matched, relevant.keys - matched.keys,
                matched.keys.map { key -> key + " matches ProductMaster" })
        }.sortedWith(compareByDescending<ProductMatch> { it.matchedAttributes.size }
            .thenBy { it.unmatchedCandidateAttributes.size })
    }

    private fun same(a: String, b: String?) = !b.isNullOrBlank() && normalize(a) == normalize(b)
    private fun normalize(value: String) = value.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9%]+"), " ").trim().replace(Regex("\\s+"), " ")
}

object ProductIdentificationEngine {
    fun identify(
        observations: List<ProductObservation>,
        knowledge: OfflineProductKnowledge = EmbeddedOfflineProductKnowledge,
        facilityProducts: List<ProductMaster> = emptyList(),
        matcher: FacilityProductMatcher = ProductMasterMatcher
    ): ProductIdentificationResult {
        val classification = classify(observations, knowledge)
        val candidate = generateCandidate(observations, classification, knowledge)
        val schema = ProductAttributeSchemas.forCategory(classification.category)
        val missing = candidate?.attributes?.let { attrs ->
            schema.rules.filter { it.requirement == AttributeRequirement.REQUIRED && attrs[it.attribute].isNullOrBlank() }
                .map { it.attribute }.toSet()
        } ?: emptySet()
        val contradictions = detectContradictions(observations, classification.category, knowledge)
        val matches = if (candidate != null && contradictions.isEmpty()) matcher.match(candidate, facilityProducts) else emptyList()
        val state = when {
            contradictions.isNotEmpty() -> IdentificationState.CONFLICT
            classification.category == ProductKnowledgeCategory.UNKNOWN -> IdentificationState.UNKNOWN
            candidate == null -> IdentificationState.NEEDS_MORE_EVIDENCE
            matches.size > 1 -> IdentificationState.AMBIGUOUS
            matches.size == 1 && missing.isEmpty() -> IdentificationState.IDENTIFIED
            matches.isEmpty() && missing.isNotEmpty() -> IdentificationState.NEEDS_MORE_EVIDENCE
            matches.isEmpty() -> IdentificationState.UNKNOWN
            else -> IdentificationState.NEEDS_MORE_EVIDENCE
        }
        val explanation = when (state) {
            IdentificationState.IDENTIFIED -> "One facility ProductMaster matches the supported observed attributes."
            IdentificationState.AMBIGUOUS -> "Multiple facility ProductMaster records remain plausible."
            IdentificationState.CONFLICT -> "Conflicting observations prevent a safe identification."
            IdentificationState.NEEDS_MORE_EVIDENCE -> "The current evidence does not satisfy the category's required attributes."
            IdentificationState.UNKNOWN -> "No sufficiently supported facility product identity was established."
        }
        return ProductIdentificationResult(state, classification, candidate, matches, observations,
            contradictions, missing, explanation, knowledge.knowledgeVersion, knowledge.schemaVersion)
    }

    private fun classify(observations: List<ProductObservation>, knowledge: OfflineProductKnowledge): CategoryClassification {
        val scores = ProductKnowledgeCategory.entries.associateWith { category ->
            observations.sumOf { observation ->
                val text = observation.normalizedValue ?: observation.rawValue
                knowledge.conceptsFor(text).filter { category in it.categories }.sumOf { concept ->
                    val weight = when (concept.type) {
                        KnowledgeConceptType.PRODUCT_TYPE -> 3.0
                        KnowledgeConceptType.DOSAGE_FORM -> 2.0
                        KnowledgeConceptType.TERMINOLOGY -> 1.0
                        else -> 0.5
                    }
                    weight * (observation.confidence ?: 0.5)
                }
            }
        }
        val ranked = scores.entries.sortedByDescending { it.value }
        val top = ranked.firstOrNull()
        if (top == null || top.value <= 0.0) return CategoryClassification(
            ProductKnowledgeCategory.UNKNOWN, 0f, emptyList(), "No category evidence was found."
        )
        val second = ranked.getOrNull(1)?.value ?: 0.0
        if (top.value < 1.0 || top.value <= second) return CategoryClassification(
            ProductKnowledgeCategory.UNKNOWN, 0f,
            observations.filter { knowledge.conceptsFor(it.normalizedValue ?: it.rawValue).isNotEmpty() },
            "Category evidence is too weak or non-distinctive."
        )
        val confidence = (top.value / (top.value + second + 1.0)).toFloat().coerceIn(0f, 1f)
        val supporting = observations.filter {
            knowledge.conceptsFor(it.normalizedValue ?: it.rawValue).any { concept -> top.key in concept.categories }
        }
        return CategoryClassification(top.key, confidence, supporting,
            "Category supported by " + supporting.size + " observation(s).")
    }

    private fun generateCandidate(
        observations: List<ProductObservation>,
        classification: CategoryClassification,
        knowledge: OfflineProductKnowledge
    ): IdentificationCandidate? {
        if (classification.category == ProductKnowledgeCategory.UNKNOWN) return null
        val text = observations.filter { it.source == ObservationSource.OCR || it.source == ObservationSource.USER }
            .joinToString("\n") { it.normalizedValue ?: it.rawValue }
        val attributes = linkedMapOf<String, String>()
        fun firstConcept(type: KnowledgeConceptType): String? =
            knowledge.concepts(type).firstOrNull { concept ->
                classification.category in concept.categories &&
                    concept.aliases.any { alias ->
                        Regex("\\b" + Regex.escape(alias) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
                    }
            }?.canonical
        firstConcept(KnowledgeConceptType.PRODUCT_TYPE)?.let { attributes["productType"] = it }
        firstConcept(KnowledgeConceptType.DOSAGE_FORM)?.let { attributes["dosageForm"] = it }
        firstConcept(KnowledgeConceptType.ROUTE)?.let { attributes["route"] = it }
        Regex("""\b\d+(?:[.,]\d+)?\s*(?:mg|mcg|µg|ug|g|kg|ml|mL|%)\b""", RegexOption.IGNORE_CASE)
            .find(text)?.value?.let { attributes["strength"] = it.replace(",", ".") }
        return IdentificationCandidate(classification.category, attributes, classification.supportingObservations)
    }

    private fun detectContradictions(
        observations: List<ProductObservation>,
        category: ProductKnowledgeCategory,
        knowledge: OfflineProductKnowledge
    ): List<String> {
        val conflicts = mutableListOf<String>()
        val strengths = observations.mapNotNull {
            Regex("""\b\d+(?:[.,]\d+)?\s*(?:mg|mcg|µg|ug|g|kg|ml|mL|%)\b""", RegexOption.IGNORE_CASE)
                .find(it.rawValue)?.value?.lowercase(Locale.ROOT)?.replace(",", ".")
        }.distinct()
        if (strengths.size > 1) conflicts += "Conflicting strength observations: " + strengths.joinToString()
        if (category == ProductKnowledgeCategory.MEDICINE) {
            val forms = observations.flatMap {
                knowledge.conceptsFor(it.normalizedValue ?: it.rawValue, KnowledgeConceptType.DOSAGE_FORM)
            }.map { it.canonical }.distinct()
            if (forms.size > 1) conflicts += "Conflicting dosage-form observations: " + forms.joinToString()
        }
        return conflicts
    }
}

object ProductObservationFactory {
    fun fromOcr(results: List<OcrResult>) = results.filter { it.text.isNotBlank() }.map {
        ProductObservation(it.text, type = ObservationType.TEXT, source = ObservationSource.OCR,
            confidence = it.confidence, sourceImageUri = it.sourceImageUri)
    }
    fun fromBarcodes(results: List<BarcodeResult>, sourceImageUri: String? = null) = results.map {
        ProductObservation(it.rawValue, type = ObservationType.BARCODE, source = ObservationSource.BARCODE,
            confidence = 1f, sourceImageUri = sourceImageUri)
    }
}
