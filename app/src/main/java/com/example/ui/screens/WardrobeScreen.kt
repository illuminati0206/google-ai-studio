package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.AsyncUiState
import com.example.data.model.AtelierConstants
import com.example.data.model.SavedOutfit
import com.example.data.model.WardrobeItem
import com.example.domain.CompatibleOutfitProposal
import com.example.ui.components.AtelierEmptyStateCard
import com.example.ui.components.AtelierErrorCard
import com.example.ui.components.AtelierTagPill
import com.example.ui.components.ClosetConditionHealthSection
import com.example.ui.components.ColorSwatchDot
import com.example.ui.components.ConditionAssessmentDialog
import com.example.ui.components.EditorialSectionHeader
import com.example.ui.components.MaterialTypeFilterSection
import com.example.ui.viewmodel.AtelierViewModel
import com.example.util.ImageStorageHelper
import java.io.File

@Composable
fun WardrobeScreen(
    viewModel: AtelierViewModel,
    onTryIn3dStudio: () -> Unit
) {
    val wardrobeState by viewModel.wardrobeState.collectAsStateWithLifecycle()
    val savedOutfitsState by viewModel.savedOutfitsState.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedMixMatchIds.collectAsStateWithLifecycle()
    val liveEvaluation by viewModel.liveMixMatchEvaluation.collectAsStateWithLifecycle()
    val algorithmicProposals by viewModel.algorithmicOutfitProposals.collectAsStateWithLifecycle()
    val conditionInspectingItemId by viewModel.conditionInspectingItemId.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf("All") }
    var selectedMaterial by remember { mutableStateOf("All") }
    var selectedKeepFilter by remember { mutableStateOf("All") }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showSaveMixDialog by remember { mutableStateOf(false) }
    var inspectingConditionItemId by remember { mutableStateOf<String?>(null) }

    val allItems = (wardrobeState as? AsyncUiState.Success)?.data.orEmpty()
    val filteredItems = remember(allItems, selectedCategory, selectedMaterial, selectedKeepFilter) {
        allItems.filter { item ->
            val catMatch = selectedCategory == "All" || item.normalizedCategory() == selectedCategory
            val matMatch = selectedMaterial == "All" || item.resolvedMaterial().displayName == selectedMaterial
            val keepMatch = selectedKeepFilter == "All" || item.keepDecision == selectedKeepFilter
            catMatch && matMatch && keepMatch
        }
    }
    val savedOutfits = (savedOutfitsState as? AsyncUiState.Success)?.data.orEmpty()
    val activeConditionItem = remember(allItems, inspectingConditionItemId) {
        allItems.firstOrNull { it.id == inspectingConditionItemId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            EditorialSectionHeader(
                eyebrow = "VIRTUAL WARDROBE",
                title = "Closet, Shoes & Mix",
                actionLabel = "+ Upload Item",
                onActionClick = {
                    viewModel.resetGarmentTagState()
                    showUploadDialog = true
                }
            )
        }

        // Interactive Mix-and-Match Live Color-Theory & Material Comfort Canvas
        item {
            MixMatchCompatibilityCard(
                selectedCount = selectedIds.size,
                evaluation = liveEvaluation,
                onClear = { viewModel.clearMixMatchSelection() },
                onSaveOutfit = { showSaveMixDialog = true },
                onTryIn3d = {
                    viewModel.render3dVirtualTryOn(
                        customOutfitPrompt = "",
                        occasion = viewModel.selectedOccasion.value
                    )
                    onTryIn3dStudio()
                }
            )
        }

        // Category Filter Row (Includes Shoes!)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "CATEGORY (APPAREL & SHOES)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == "All",
                            onClick = { selectedCategory = "All" },
                            label = { Text("All (${allItems.size})") }
                        )
                    }
                    items(AtelierConstants.GARMENT_CATEGORIES) { cat ->
                        val count = allItems.count { it.normalizedCategory() == cat }
                        val label = if (count > 0) "$cat ($count)" else cat
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(label) },
                            modifier = Modifier.testTag("category_filter_${cat.lowercase().replace(" ", "_")}")
                        )
                    }
                }
            }
        }

        // Material Type Categorization UI Component
        item {
            MaterialTypeFilterSection(
                allItems = allItems,
                selectedMaterial = selectedMaterial,
                onSelectMaterial = { selectedMaterial = it }
            )
        }

        // Closet Condition & Keep/Replace Health Audit Filter
        item {
            ClosetConditionHealthSection(
                allItems = allItems,
                selectedKeepFilter = selectedKeepFilter,
                onSelectKeepFilter = { selectedKeepFilter = it }
            )
        }

        // Wardrobe Items List
        when (val state = wardrobeState) {
            is AsyncUiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is AsyncUiState.Error -> {
                item { AtelierErrorCard(message = state.message) }
            }
            is AsyncUiState.Success, is AsyncUiState.Idle -> {
                if (filteredItems.isEmpty()) {
                    item {
                        val filterDesc = listOfNotNull(
                            selectedCategory.takeIf { it != "All" },
                            selectedMaterial.takeIf { it != "All" },
                            selectedKeepFilter.takeIf { it != "All" }
                        ).joinToString(" • ")
                        AtelierEmptyStateCard(
                            title = if (allItems.isEmpty()) "Your Virtual Closet is Ready" else "No items match $filterDesc",
                            subtitle = "Upload clothes or shoes with Photo Picker or Camera. Atelier AI auto-tags material type (Silk, Cotton, Wool, Leather...), color, and condition (Keep / Repair / Replace).",
                            actionLabel = if (allItems.isEmpty()) "Upload Garment or Shoes" else "Reset Filters",
                            onAction = {
                                if (allItems.isEmpty()) {
                                    viewModel.resetGarmentTagState()
                                    showUploadDialog = true
                                } else {
                                    selectedCategory = "All"
                                    selectedMaterial = "All"
                                    selectedKeepFilter = "All"
                                }
                            },
                            modifier = Modifier.testTag("empty_upload_garment_button")
                        )
                    }
                } else {
                    items(filteredItems, key = { it.id }) { item ->
                        WardrobeGarmentRowCard(
                            item = item,
                            isSelected = item.id in selectedIds,
                            isInspectingCondition = conditionInspectingItemId == item.id,
                            onToggleSelect = { viewModel.toggleMixMatchItem(item.id) },
                            onOpenConditionDialog = { inspectingConditionItemId = item.id },
                            onDelete = { viewModel.deleteWardrobeItem(item.id) }
                        )
                    }
                }
            }
        }

        // Algorithmic Color-Theory + Material Comfort + Weather Proposals
        if (algorithmicProposals.isNotEmpty()) {
            item {
                EditorialSectionHeader(
                    eyebrow = "COLOR, MATERIAL & WEATHER ENGINE",
                    title = "Instant Compatible Outfits"
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(algorithmicProposals) { proposal ->
                        AlgorithmicProposalCard(
                            proposal = proposal,
                            onLoadIntoCanvas = {
                                viewModel.selectMixMatchSet(proposal.items.map { it.id })
                            },
                            onTry3d = {
                                viewModel.selectMixMatchSet(proposal.items.map { it.id })
                                viewModel.render3dVirtualTryOn(
                                    customOutfitPrompt = "",
                                    occasion = viewModel.selectedOccasion.value
                                )
                                onTryIn3dStudio()
                            }
                        )
                    }
                }
            }
        }

        // Saved Lookbook Shelf
        if (savedOutfits.isNotEmpty()) {
            item {
                EditorialSectionHeader(
                    eyebrow = "SAVED LOOKBOOK",
                    title = "Your Outfit Combinations (${savedOutfits.size})"
                )
            }
            items(savedOutfits, key = { it.id }) { outfit ->
                SavedOutfitRowCard(
                    outfit = outfit,
                    onDelete = { viewModel.deleteSavedOutfit(outfit.id) }
                )
            }
        }
    }

    if (showUploadDialog) {
        UploadGarmentDialog(
            viewModel = viewModel,
            onDismiss = { showUploadDialog = false }
        )
    }

    if (showSaveMixDialog) {
        SaveMixMatchDialog(
            defaultTitle = liveEvaluation.title,
            onDismiss = { showSaveMixDialog = false },
            onSave = { title, occasion ->
                viewModel.saveCurrentMixMatchOutfit(title, occasion)
                showSaveMixDialog = false
            }
        )
    }

    if (activeConditionItem != null) {
        ConditionAssessmentDialog(
            item = activeConditionItem,
            isInspecting = conditionInspectingItemId == activeConditionItem.id,
            onRunAiInspect = { viewModel.inspectItemConditionWithAi(activeConditionItem) },
            onSaveCondition = { status, decision, notes ->
                viewModel.updateItemCondition(activeConditionItem.id, status, decision, notes)
            },
            onTrackReplacementDeal = {
                viewModel.addDiscountAlert(
                    itemName = "${activeConditionItem.colorName} ${activeConditionItem.name} Replacement",
                    brandName = activeConditionItem.brand.ifBlank { "Sustainable Atelier Pick" },
                    category = activeConditionItem.normalizedCategory(),
                    targetDiscountPercent = 25,
                    currentDiscountPercent = 15,
                    ecoCertified = true,
                    seasonalTag = "Closet Refresh Edit"
                )
                inspectingConditionItemId = null
            },
            onDismiss = { inspectingConditionItemId = null }
        )
    }
}

@Composable
private fun MixMatchCompatibilityCard(
    selectedCount: Int,
    evaluation: CompatibleOutfitProposal,
    onClear: () -> Unit,
    onSaveOutfit: () -> Unit,
    onTryIn3d: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MIX & MATCH CANVAS ($selectedCount SELECTED)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (selectedCount > 0) {
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }

            if (selectedCount == 0) {
                Text(
                    text = "Tap any garments or shoes below to combine them. Our HSL color-theory, material comfort & weather engine scores compatibility in real time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AtelierTagPill(
                            text = "${evaluation.overallScore}% Overall",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    item {
                        AtelierTagPill(
                            text = "Comfort ${evaluation.materialComfortScore}%",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    item {
                        AtelierTagPill(
                            text = evaluation.colorHarmonyType,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = evaluation.trendAlignmentTag,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = evaluation.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveOutfit,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_mix_match_button")
                    ) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Look")
                    }
                    Button(
                        onClick = onTryIn3d,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mix_match_3d_tryon_button")
                    ) {
                        Icon(Icons.Outlined.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("3D Try-On")
                    }
                }
            }
        }
    }
}

@Composable
private fun WardrobeGarmentRowCard(
    item: WardrobeItem,
    isSelected: Boolean,
    isInspectingCondition: Boolean,
    onToggleSelect: () -> Unit,
    onOpenConditionDialog: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val normalizedCat = item.normalizedCategory()
    val material = item.resolvedMaterial()

    val (condBg, condFg) = when (item.keepDecision) {
        "Donate / Replace" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "Repair / Dry Clean" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onToggleSelect() }
            .testTag("wardrobe_item_${item.id}"),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.localImagePath.isNotBlank() && File(item.localImagePath).exists()) {
                        AsyncImage(
                            model = File(item.localImagePath),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (normalizedCat == "Shoes") Icons.Outlined.Hiking else Icons.Outlined.Checkroom,
                            contentDescription = normalizedCat,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Selected for outfit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ColorSwatchDot(hexColor = item.colorHex, sizeDp = 12)
                        Text(
                            text = "${normalizedCat.uppercase()} • ${material.displayName.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.colorName} • ${item.fabric} • ${item.season}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            AtelierTagPill(
                                text = "${item.conditionStatus} • ${item.keepDecision}",
                                containerColor = condBg,
                                contentColor = condFg
                            )
                        }
                        item {
                            AtelierTagPill(text = item.formality)
                        }
                        item {
                            AtelierTagPill(
                                text = "Eco ${item.ecoScore}",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                icon = Icons.Outlined.Eco
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onOpenConditionDialog,
                        modifier = Modifier.testTag("condition_check_${item.id}")
                    ) {
                        if (isInspectingCondition) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.FactCheck,
                                contentDescription = "Inspect item condition and keep decision",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove item",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (item.conditionNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Condition Note: ${item.conditionNotes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AlgorithmicProposalCard(
    proposal: CompatibleOutfitProposal,
    onLoadIntoCanvas: () -> Unit,
    onTry3d: () -> Unit
) {
    Surface(
        modifier = Modifier.width(276.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AtelierTagPill(
                    text = "${proposal.overallScore}% Score • ${proposal.materialComfortScore}% Comfort",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    proposal.items.take(4).forEach {
                        ColorSwatchDot(hexColor = it.colorHex, sizeDp = 12)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = proposal.trendAlignmentTag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = proposal.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = proposal.items.joinToString(" + ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = proposal.weatherNote.ifBlank { proposal.colorHarmonyType },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLoadIntoCanvas, modifier = Modifier.weight(1f)) {
                    Text("Select")
                }
                Button(onClick = onTry3d, modifier = Modifier.weight(1f)) {
                    Text("3D View")
                }
            }
        }
    }
}

@Composable
private fun SavedOutfitRowCard(
    outfit: SavedOutfit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (outfit.tryOnImagePath.isNotBlank() && File(outfit.tryOnImagePath).exists()) {
                AsyncImage(
                    model = File(outfit.tryOnImagePath),
                    contentDescription = outfit.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = outfit.occasion.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = outfit.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (outfit.itemSummary.isNotBlank()) {
                    Text(
                        text = outfit.itemSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Harmony ${outfit.colorHarmonyScore}% • Fit ${outfit.fitScore}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete saved outfit")
            }
        }
    }
}

@Composable
private fun UploadGarmentDialog(
    viewModel: AtelierViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tagState by viewModel.garmentTagState.collectAsStateWithLifecycle()

    var brand by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Top") }
    var colorName by remember { mutableStateOf("Warm Ivory") }
    var colorHex by remember { mutableStateOf("#F2ECE1") }
    var materialType by remember { mutableStateOf("Cotton") }
    var fabric by remember { mutableStateOf("Organic Cotton") }
    var season by remember { mutableStateOf("All Season") }
    var formality by remember { mutableStateOf("Smart Casual") }
    var conditionStatus by remember { mutableStateOf("Good") }
    var keepDecision by remember { mutableStateOf("Keep") }
    var conditionNotes by remember { mutableStateOf("") }
    var ecoScore by remember { mutableIntStateOf(80) }
    var notes by remember { mutableStateOf("") }
    var webSourceSummary by remember { mutableStateOf("") }
    var savedImagePath by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var hasAutoFilled by remember { mutableStateOf(false) }
    var showAdvancedEditor by remember { mutableStateOf(false) }

    LaunchedEffect(tagState) {
        if (tagState is AsyncUiState.Success) {
            val tagged = (tagState as AsyncUiState.Success).data
            if (tagged.name.isNotBlank()) name = tagged.name
            if (tagged.brand.isNotBlank()) brand = tagged.brand
            if (tagged.category in AtelierConstants.GARMENT_CATEGORIES) category = tagged.category
            colorName = tagged.colorName
            colorHex = tagged.colorHex
            if (tagged.materialType in AtelierConstants.MATERIAL_TYPES) materialType = tagged.materialType
            fabric = tagged.fabric
            if (tagged.season in AtelierConstants.SEASONS) season = tagged.season
            if (tagged.formality in AtelierConstants.FORMALITIES) formality = tagged.formality
            if (tagged.conditionStatus in AtelierConstants.CONDITION_STATUSES) conditionStatus = tagged.conditionStatus
            if (tagged.keepDecision in AtelierConstants.KEEP_DECISIONS) keepDecision = tagged.keepDecision
            conditionNotes = tagged.conditionNotes
            ecoScore = tagged.ecoScore
            notes = tagged.stylingNote
            webSourceSummary = tagged.webSourceSummary
            hasAutoFilled = true
        }
    }

    fun handlePickedUri(uri: Uri) {
        val result: Pair<String, Bitmap>? = ImageStorageHelper.persistCompressedImage(
            context = context,
            sourceUri = uri,
            subDir = "wardrobe",
            filePrefix = "garment"
        )
        if (result != null) {
            savedImagePath = result.first
            viewModel.autoTagGarmentPhoto(
                bitmap = result.second,
                brandHint = brand,
                itemHint = name
            )
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) handlePickedUri(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            handlePickedUri(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, _) = ImageStorageHelper.createCameraCaptureUri(context, "garment")
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        title = {
            Column {
                Text("Add Garment or Shoes", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Snap a photo or enter Brand — we check exact material & details online automatically.",
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
                // Step 1: Basic Inputs (Photo + Brand + Basic Name)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                val (uri, _) = ImageStorageHelper.createCameraCaptureUri(context, "garment")
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("capture_garment_camera_button")
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Camera")
                    }
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_garment_photo_button")
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gallery")
                    }
                }

                if (savedImagePath.isNotBlank() && File(savedImagePath).exists()) {
                    AsyncImage(
                        model = File(savedImagePath),
                        contentDescription = "Selected item preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand Name (for Online Lookup)") },
                    placeholder = { Text("e.g. Zara, Nike, Uniqlo, Fabindia, H&M") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("garment_brand_input")
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Basic Item / Model Name (Optional)") },
                    placeholder = { Text("e.g. Linen Shirt, Air Force 1, Wool Coat") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("garment_name_input")
                )

                Button(
                    onClick = {
                        viewModel.lookupGarmentDetailsFromWeb(
                            savedImagePath = savedImagePath,
                            brandHint = brand,
                            itemHint = name
                        )
                    },
                    enabled = tagState !is AsyncUiState.Loading &&
                        (savedImagePath.isNotBlank() || brand.isNotBlank() || name.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lookup_web_details_button")
                ) {
                    if (tagState is AsyncUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking Brand & Material Online...")
                    } else {
                        Icon(Icons.Outlined.TravelExplore, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch Material & Details Online")
                    }
                }

                when (val s = tagState) {
                    is AsyncUiState.Error -> {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> Unit
                }

                // Step 2: Auto-Filled Summary Card (shows everything auto-fetched so user doesn't have to type it!)
                if (hasAutoFilled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            AtelierTagPill(
                                text = webSourceSummary.ifBlank { "Auto-Filled from Photo & Brand Specs" },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                icon = Icons.Outlined.AutoAwesome
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ColorSwatchDot(hexColor = colorHex, sizeDp = 12)
                                Text(
                                    text = "$category • $colorName • $materialType ($fabric)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Season: $season • Formality: $formality • Eco $ecoScore/100",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Condition: $conditionStatus ($keepDecision)${if (conditionNotes.isNotBlank()) " — $conditionNotes" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expandable toggle to customize any auto-filled fields manually if desired
                TextButton(
                    onClick = { showAdvancedEditor = !showAdvancedEditor },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_advanced_garment_fields")
                ) {
                    Icon(
                        imageVector = if (showAdvancedEditor) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (showAdvancedEditor) "Hide Detailed Fields"
                        else if (hasAutoFilled) "Edit Auto-Filled Details (Optional)"
                        else "Enter Detailed Fields Manually (Optional)"
                    )
                }

                if (showAdvancedEditor) {
                    Text("Category (Apparel & Shoes)", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AtelierConstants.GARMENT_CATEGORIES) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }

                    Text("Material Type", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AtelierConstants.MATERIAL_TYPES) { mat ->
                            FilterChip(
                                selected = materialType == mat,
                                onClick = {
                                    materialType = mat
                                    if (fabric.isBlank() || fabric in AtelierConstants.MATERIAL_TYPES || fabric == "Organic Cotton") {
                                        fabric = mat
                                    }
                                },
                                label = { Text(mat) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = colorName,
                            onValueChange = { colorName = it },
                            label = { Text("Color") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fabric,
                            onValueChange = { fabric = it },
                            label = { Text("Fabric Detail") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Condition Status", style = MaterialTheme.typography.labelMedium)
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

                    Text("Keep or Replace Decision", style = MaterialTheme.typography.labelMedium)
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
                        label = { Text("Condition Notes (Optional)") },
                        placeholder = { Text("e.g. Crisp stitching, slight heel wear, dry-clean soon") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Formality", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AtelierConstants.FORMALITIES) { f ->
                            FilterChip(
                                selected = formality == f,
                                onClick = { formality = f },
                                label = { Text(f) }
                            )
                        }
                    }

                    Text("Season", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AtelierConstants.SEASONS) { s ->
                            FilterChip(
                                selected = season == s,
                                onClick = { season = s },
                                label = { Text(s) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val autoName = name.trim().ifEmpty {
                        listOf(brand.trim(), colorName.trim(), category).filter { it.isNotBlank() }.joinToString(" ")
                    }
                    viewModel.addWardrobeItem(
                        WardrobeItem(
                            name = autoName,
                            category = category,
                            colorName = colorName.trim().ifEmpty { "Neutral" },
                            colorHex = colorHex,
                            materialType = materialType,
                            fabric = fabric.trim().ifEmpty { materialType },
                            season = season,
                            formality = formality,
                            conditionStatus = conditionStatus,
                            keepDecision = keepDecision,
                            conditionNotes = conditionNotes.trim(),
                            brand = brand.trim(),
                            localImagePath = savedImagePath,
                            ecoScore = ecoScore,
                            notes = listOf(notes.trim(), webSourceSummary.trim()).filter { it.isNotBlank() }.joinToString(" • ")
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_add_garment_button")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save to Closet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SaveMixMatchDialog(
    defaultTitle: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var occasion by remember { mutableStateOf(AtelierConstants.OCCASIONS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        title = { Text("Save Outfit Combination", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Outfit Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Occasion", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AtelierConstants.OCCASIONS) { occ ->
                        FilterChip(
                            selected = occasion == occ,
                            onClick = { occasion = occ },
                            label = { Text(occ) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, occasion) }) {
                Text("Save Look")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
