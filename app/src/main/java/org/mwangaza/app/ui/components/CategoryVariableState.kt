package org.mwangaza.app.ui.components

import org.mwangaza.app.scanner.interpretation.CategoryVariableDefinition
import org.mwangaza.app.scanner.interpretation.CategoryVariableProposal

fun replaceCategoryDefinitionValues(
    proposals: List<CategoryVariableProposal>,
    definitionKey: String,
    replacement: List<CategoryVariableProposal>
): List<CategoryVariableProposal> =
    proposals.filterNot { it.definitionKey == definitionKey } + replacement

fun unresolvedSingleDefinitionKeys(
    definitions: List<CategoryVariableDefinition>,
    proposals: List<CategoryVariableProposal>
): List<String> =
    definitions
        .filterNot { it.multiValued }
        .mapNotNull { definition ->
            val count = proposals.count {
                it.definitionKey == definition.definitionKey && it.value.isNotBlank()
            }
            definition.definitionKey.takeIf { count > 1 }
        }
