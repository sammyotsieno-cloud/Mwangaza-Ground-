package org.mwangaza.app.ui.components

import core.domain.model.ProductType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mwangaza.app.scanner.interpretation.CategoryExtractionProfiles
import org.mwangaza.app.scanner.interpretation.CategoryVariableProposal

class CategoryVariableStateTest {

    @Test
    fun multi_value_replacement_preserves_all_values_for_same_definition() {
        val first = CategoryVariableProposal(
            definitionKey = "key_nutrients",
            valueType = "QUANTITY",
            value = "Protein 10 g",
            multiValued = true
        )
        val second = first.copy(value = "Iron 5 mg")
        val replacement = second.copy(value = "Iron 6 mg")

        val result = replaceCategoryDefinitionValues(
            proposals = listOf(first, second),
            definitionKey = "key_nutrients",
            replacement = listOf(first, replacement)
        )

        assertEquals(2, result.size)
        assertEquals("Protein 10 g", result[0].value)
        assertEquals("Iron 6 mg", result[1].value)
    }

    @Test
    fun single_value_conflict_is_explicitly_unresolved() {
        val definition = CategoryExtractionProfiles
            .definitions(ProductType.MEDICINE)
            .first { it.definitionKey == "strength" }

        val proposals = listOf(
            CategoryVariableProposal(
                definitionKey = "strength",
                valueType = "QUANTITY",
                value = "250 mg"
            ),
            CategoryVariableProposal(
                definitionKey = "strength",
                valueType = "QUANTITY",
                value = "250 mg/5 mL"
            )
        )

        val unresolved = unresolvedSingleDefinitionKeys(listOf(definition), proposals)

        assertTrue(unresolved.contains("strength"))
    }

    @Test
    fun multi_valued_profile_definition_is_not_marked_ambiguous() {
        val definitions = CategoryExtractionProfiles
            .definitions(ProductType.NUTRITION_THERAPEUTIC_FOOD)

        val nutrients = definitions.first { it.definitionKey == "key_nutrients" }
        val proposals = listOf(
            CategoryVariableProposal(
                definitionKey = "key_nutrients",
                valueType = "QUANTITY",
                value = "Protein 10 g",
                multiValued = true
            ),
            CategoryVariableProposal(
                definitionKey = "key_nutrients",
                valueType = "QUANTITY",
                value = "Iron 5 mg",
                multiValued = true
            )
        )

        val unresolved = unresolvedSingleDefinitionKeys(definitions, proposals)

        assertTrue(nutrients.multiValued)
        assertTrue(unresolved.isEmpty())
    }
}
