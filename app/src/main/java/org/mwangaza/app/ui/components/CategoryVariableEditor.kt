package org.mwangaza.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mwangaza.app.scanner.interpretation.CategoryVariableDefinition
import org.mwangaza.app.scanner.interpretation.CategoryVariableProposal

private fun labelFor(key: String): String =
    key.replace('_', ' ').split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }

private fun isTrueValue(value: String): Boolean =
    value.trim().equals("true", true) ||
        value.trim().equals("yes", true) ||
        value.trim().equals("single-use", true) ||
        value.trim().equals("single use", true)

@Composable
fun CategoryVariableEditor(
    definitions: List<CategoryVariableDefinition>,
    proposals: List<CategoryVariableProposal>,
    onProposalsChange: (List<CategoryVariableProposal>) -> Unit,
    modifier: Modifier = Modifier,
    showEmptyDefinitions: Boolean = true
) {
    val selectedCandidates = remember { mutableStateMapOf<String, Int>() }
    val grouped = proposals.groupBy { it.definitionKey }
    val orderedKeys = definitions.map { it.definitionKey } +
        proposals.map { it.definitionKey }.filter { key -> definitions.none { it.definitionKey == key } }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        orderedKeys.distinct().forEach { key ->
            val definition = definitions.firstOrNull { it.definitionKey == key }
                ?: CategoryVariableDefinition(
                    definitionKey = key,
                    valueType = grouped[key]?.firstOrNull()?.valueType ?: "TEXT",
                    multiValued = grouped[key]?.firstOrNull()?.multiValued ?: false
                )
            val items = grouped[key].orEmpty()

            if (definition.multiValued) {
                MultiValueEditor(definition, items) { replacement ->
                    onProposalsChange(proposals.filterNot { it.definitionKey == key } + replacement)
                }
            } else {
                SingleValueEditor(
                    definition = definition,
                    proposals = items,
                    showEmpty = showEmptyDefinitions,
                    selectedIndex = selectedCandidates[key] ?: 0,
                    onSelectedIndexChange = { selectedCandidates[key] = it }
                ) { replacement ->
                    onProposalsChange(proposals.filterNot { it.definitionKey == key } + replacement)
                }
            }
        }
    }
}

@Composable
private fun SingleValueEditor(
    definition: CategoryVariableDefinition,
    proposals: List<CategoryVariableProposal>,
    showEmpty: Boolean,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onChange: (List<CategoryVariableProposal>) -> Unit
) {
    if (proposals.isEmpty()) {
        if (!showEmpty) return
        val blank = CategoryVariableProposal(
            definitionKey = definition.definitionKey,
            valueType = definition.valueType,
            value = "",
            normalizedValue = null,
            provenance = "USER_VERIFIED",
            evidence = emptyList(),
            ruleName = null,
            multiValued = false
        )
        TypedProposalField(blank) { edited ->
            onChange(listOf(edited.copy(
                normalizedValue = edited.value.trim().lowercase().ifBlank { null },
                provenance = if (edited.value.isBlank()) "OCR_STRUCTURED" else "USER_VERIFIED"
            )))
        }
        return
    }

    val safeIndex = selectedIndex.coerceIn(0, proposals.lastIndex)
    val selected = proposals[safeIndex]

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(labelFor(definition.definitionKey), style = MaterialTheme.typography.titleSmall)

            if (proposals.size > 1) {
                Text(
                    "Multiple candidates detected. Select the value to verify.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                proposals.forEachIndexed { candidateIndex, candidate ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = candidateIndex == safeIndex,
                            onClick = {
                                onSelectedIndexChange(candidateIndex)
                                onChange(listOf(candidate.copy(
                                    normalizedValue = candidate.value.trim().lowercase().ifBlank { null },
                                    provenance = "USER_VERIFIED"
                                )))
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(candidate.value.ifBlank { "Empty candidate" })
                            ProposalMetadata(candidate)
                        }
                    }
                }
            }

            TypedProposalField(selected) { edited ->
                onChange(listOf(edited.copy(
                    normalizedValue = edited.value.trim().lowercase().ifBlank { null },
                    provenance = "USER_VERIFIED"
                )))
            }
            ProposalMetadata(selected)
        }
    }
}

@Composable
private fun MultiValueEditor(
    definition: CategoryVariableDefinition,
    proposals: List<CategoryVariableProposal>,
    onChange: (List<CategoryVariableProposal>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(labelFor(definition.definitionKey), style = MaterialTheme.typography.titleSmall)

        proposals.forEachIndexed { index, proposal ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypedProposalField(proposal) { edited ->
                        val copy = proposals.toMutableList()
                        copy[index] = edited.copy(
                            normalizedValue = edited.value.trim().lowercase().ifBlank { null },
                            provenance = "USER_VERIFIED"
                        )
                        onChange(copy)
                    }
                    ProposalMetadata(proposal)
                    Text(
                        "Value " + (index + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { onChange(proposals.filterIndexed { i, _ -> i != index }) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Remove value") }
                }
            }
        }

        Button(
            onClick = {
                onChange(proposals + CategoryVariableProposal(
                    definitionKey = definition.definitionKey,
                    valueType = definition.valueType,
                    value = "",
                    normalizedValue = null,
                    provenance = "USER_VERIFIED",
                    evidence = emptyList(),
                    ruleName = null,
                    multiValued = true
                ))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add " + labelFor(definition.definitionKey)) }
    }
}

@Composable
private fun TypedProposalField(
    proposal: CategoryVariableProposal,
    onChange: (CategoryVariableProposal) -> Unit
) {
    when (proposal.valueType.uppercase()) {
        "BOOLEAN" -> Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isTrueValue(proposal.value),
                onCheckedChange = { checked ->
                    onChange(proposal.copy(
                        value = checked.toString(),
                        normalizedValue = checked.toString(),
                        provenance = "USER_VERIFIED"
                    ))
                }
            )
            Text(labelFor(proposal.definitionKey))
        }
        "QUANTITY" -> OutlinedTextField(
            value = proposal.value,
            onValueChange = { onChange(proposal.copy(value = it)) },
            label = { Text(labelFor(proposal.definitionKey) + " — quantity") },
            placeholder = { Text("Preserve the complete value and unit structure") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        "DIMENSION" -> OutlinedTextField(
            value = proposal.value,
            onValueChange = { onChange(proposal.copy(value = it)) },
            label = { Text(labelFor(proposal.definitionKey) + " — dimension") },
            placeholder = { Text("e.g. 10 cm x 20 cm or 22 G") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        "TEXT" -> OutlinedTextField(
            value = proposal.value,
            onValueChange = { onChange(proposal.copy(value = it)) },
            label = { Text(labelFor(proposal.definitionKey)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        else -> {
            OutlinedTextField(
                value = proposal.value,
                onValueChange = { onChange(proposal.copy(value = it)) },
                label = { Text(labelFor(proposal.definitionKey) + " (advanced)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Value type '" + proposal.valueType + "' has no dedicated editor; the value is preserved without reinterpretation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProposalMetadata(proposal: CategoryVariableProposal) {
    if (proposal.provenance.isNotBlank()) {
        Text(
            "Source: " + humanProvenance(proposal.provenance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (proposal.evidence.isNotEmpty()) {
        Text(
            "Evidence: " + proposal.evidence.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun humanProvenance(value: String): String = when (value) {
    "OCR_EXPLICIT" -> "Package text — explicit statement"
    "OCR_STRUCTURED" -> "Package text — structured measurement"
    "OCR_LAYOUT" -> "Package layout/context"
    "OCR_INFERRED" -> "Package context — inferred"
    "USER_VERIFIED" -> "Human verified"
    else -> value.ifBlank { "Not recorded" }
}
