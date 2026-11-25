package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.data.model.AtelierConstants
import com.example.data.model.MaterialType
import com.example.data.model.WardrobeItem

@Composable
fun MaterialTypeFilterSection(
    allItems: List<WardrobeItem>,
    selectedMaterial: String,
    onSelectMaterial: (String) -> Unit
) {
    val countByMaterial = remember(allItems) {
        allItems.groupingBy { it.resolvedMaterial().displayName }.eachCount()
    }
    val activeMaterialObj = remember(selectedMaterial) {
        if (selectedMaterial == "All") null else MaterialType.fromRaw(selectedMaterial)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "MATERIAL TYPE & THERMAL COMFORT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedMaterial == "All",
                    onClick = { onSelectMaterial("All") },
                    label = { Text("All Fabrics") },
                    modifier = Modifier.testTag("material_filter_all")
                )
            }
            items(MaterialType.entries) { mat ->
                val count = countByMaterial[mat.displayName] ?: 0
                val label = if (count > 0) "${mat.displayName} ($count)" else mat.displayName
                FilterChip(
                    selected = selectedMaterial == mat.displayName,
                    onClick = { onSelectMaterial(mat.displayName) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("material_filter_${mat.name.lowercase()}")
                )
            }
        }

        if (activeMaterialObj != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AtelierTagPill(
                            text = "Warmth ${activeMaterialObj.thermalRating}/5",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            icon = Icons.Outlined.Thermostat
                        )
                        AtelierTagPill(
                            text = "Breathable: ${activeMaterialObj.breathability}",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        AtelierTagPill(
                            text = "Rain: ${activeMaterialObj.waterResistance}",
                            icon = Icons.Outlined.WaterDrop
                        )
                    }
                    Text(
                        text = "${activeMaterialObj.comfortNote} • Ideal: ${activeMaterialObj.idealSeasons.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ClosetConditionHealthSection(
    allItems: List<WardrobeItem>,
    selectedKeepFilter: String,
    onSelectKeepFilter: (String) -> Unit
) {
    val keepCount = remember(allItems) { allItems.count { it.keepDecision == "Keep" } }
    val repairCount = remember(allItems) { allItems.count { it.keepDecision == "Repair / Dry Clean" } }
    val replaceCount = remember(allItems) { allItems.count { it.keepDecision == "Donate / Replace" } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "CONDITION & KEEP AUDIT (RAKHNE CHAHIYE YA NHI)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedKeepFilter == "All",
                    onClick = { onSelectKeepFilter("All") },
                    label = { Text("All (${allItems.size})") },
                    modifier = Modifier.testTag("condition_filter_all")
                )
            }
            item {
                FilterChip(
                    selected = selectedKeepFilter == "Keep",
                    onClick = { onSelectKeepFilter("Keep") },
                    leadingIcon = {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Keep ($keepCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    modifier = Modifier.testTag("condition_filter_keep")
                )
            }
            item {
                FilterChip(
                    selected = selectedKeepFilter == "Repair / Dry Clean",
                    onClick = { onSelectKeepFilter("Repair / Dry Clean") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Repair ($repairCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("condition_filter_repair")
                )
            }
            item {
                FilterChip(
                    selected = selectedKeepFilter == "Donate / Replace",
                    onClick = { onSelectKeepFilter("Donate / Replace") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Recycling, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Replace ($replaceCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.testTag("condition_filter_replace")
                )
            }
        }
    }
}

@Composable
fun ConditionAssessmentDialog(
    item: WardrobeItem,
    isInspecting: Boolean,
    onRunAiInspect: () -> Unit,
    onSaveCondition: (conditionStatus: String, keepDecision: String, conditionNotes: String) -> Unit,
    onTrackReplacementDeal: () -> Unit,
    onDismiss: () -> Unit
) {
    var conditionStatus by remember(item.id) { mutableStateOf(item.conditionStatus.ifBlank { "Good" }) }
    var keepDecision by remember(item.id) { mutableStateOf(item.keepDecision.ifBlank { "Keep" }) }
    var conditionNotes by remember(item.id) { mutableStateOf(item.conditionNotes) }

    // Keep dialog fields in sync if AI inspection updates the Firestore item while dialog is open
    LaunchedEffect(item.conditionStatus, item.keepDecision, item.conditionNotes) {
        conditionStatus = item.conditionStatus.ifBlank { "Good" }
        keepDecision = item.keepDecision.ifBlank { "Keep" }
        conditionNotes = item.conditionNotes
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        title = {
            Column {
                Text(
                    text = "Condition & Keep Check",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "${item.name} • ${item.normalizedCategory()} • ${item.resolvedMaterial().displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRunAiInspect,
                    enabled = !isInspecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_ai_condition_check_button")
                ) {
                    if (isInspecting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Inspecting fabric & wear with AI...")
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run AI Condition Inspection")
                    }
                }

                Text(
                    text = "Current Wear Condition",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AtelierConstants.CONDITION_STATUSES) { status ->
                        FilterChip(
                            selected = conditionStatus == status,
                            onClick = {
                                conditionStatus = status
                                keepDecision = when (status) {
                                    "Worn / Damaged" -> "Donate / Replace"
                                    "Fair / Needs Care" -> "Repair / Dry Clean"
                                    else -> "Keep"
                                }
                            },
                            label = { Text(status) }
                        )
                    }
                }

                Text(
                    text = "Should You Keep It?",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AtelierConstants.KEEP_DECISIONS) { decision ->
                        FilterChip(
                            selected = keepDecision == decision,
                            onClick = { keepDecision = decision },
                            label = { Text(decision) }
                        )
                    }
                }

                OutlinedTextField(
                    value = conditionNotes,
                    onValueChange = { conditionNotes = it },
                    label = { Text("Care or Replacement Notes") },
                    placeholder = { Text("e.g. Slight pilling on cuffs, sole scuffed, still fresh") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("condition_notes_input")
                )

                if (keepDecision == "Donate / Replace") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Items marked \"Donate / Replace\" are automatically deprioritized from fresh outfit combinations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = onTrackReplacementDeal) {
                                Icon(Icons.Outlined.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Track Sustainable Replacement Deal")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveCondition(conditionStatus, keepDecision, conditionNotes)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_condition_button")
            ) {
                Text("Save Condition")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
