package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AsyncUiState
import com.example.data.model.AtelierConstants
import com.example.data.model.UserStyleProfile
import com.example.ui.components.AtelierAiLoadingCard
import com.example.ui.components.AtelierErrorCard
import com.example.ui.components.AtelierTagPill
import com.example.ui.components.EditorialSectionHeader
import com.example.ui.theme.AtelierObsidian
import com.example.ui.viewmodel.AtelierViewModel
import com.example.util.ImageStorageHelper
import com.example.util.SocialShareHelper
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TryOnStudioScreen(
    viewModel: AtelierViewModel
) {
    val context = LocalContext.current
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val wardrobeState by viewModel.wardrobeState.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedMixMatchIds.collectAsStateWithLifecycle()
    val tryOnState by viewModel.tryOnBitmapState.collectAsStateWithLifecycle()
    val activeAngle by viewModel.tryOnAngle.collectAsStateWithLifecycle()
    val bodyScanState by viewModel.bodyScanState.collectAsStateWithLifecycle()

    val profile = (profileState as? AsyncUiState.Success)?.data ?: UserStyleProfile()
    val wardrobe = (wardrobeState as? AsyncUiState.Success)?.data.orEmpty()
    val selectedItems = remember(wardrobe, selectedIds) { wardrobe.filter { it.id in selectedIds } }

    var activeMode by remember { mutableStateOf("3D_TRY_ON") }
    var customOutfitText by remember { mutableStateOf("") }
    var tryOnOccasion by remember { mutableStateOf(AtelierConstants.OCCASIONS.first()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            EditorialSectionHeader(
                eyebrow = "HIGH-FIDELITY 3D STUDIO",
                title = "Virtual Try-On & Body Fit"
            )
        }

        // Studio Mode Segmented Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = activeMode == "3D_TRY_ON",
                    onClick = { activeMode = "3D_TRY_ON" },
                    leadingIcon = { Icon(Icons.Outlined.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("3D Virtual Try-On") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_mode_3d_tab")
                )
                FilterChip(
                    selected = activeMode == "BODY_SCAN",
                    onClick = { activeMode = "BODY_SCAN" },
                    leadingIcon = { Icon(Icons.Outlined.PersonSearch, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Body Scan & Fit") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_mode_body_scan_tab")
                )
            }
        }

        if (activeMode == "3D_TRY_ON") {
            // 3D Stage Viewer
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = AtelierObsidian,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (val state = tryOnState) {
                            is AsyncUiState.Success -> {
                                Image(
                                    bitmap = state.data.asImageBitmap(),
                                    contentDescription = "3D Virtual Try-On Studio Render",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                Image(
                                    painter = painterResource(id = R.drawable.img_studio_stage),
                                    contentDescription = "Atelier 3D Fitting Stage",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    AtelierObsidian.copy(alpha = 0.25f),
                                                    AtelierObsidian.copy(alpha = 0.72f)
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        // Fit Telemetry Badges Top Overlay
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AtelierTagPill(
                                text = "${profile.gender} • ${profile.bodyType}",
                                containerColor = AtelierObsidian.copy(alpha = 0.72f),
                                contentColor = Color(0xFFFAF6F0)
                            )
                            AtelierTagPill(
                                text = "${profile.heightCm} cm",
                                containerColor = Color(0xFFDCC08A),
                                contentColor = AtelierObsidian
                            )
                        }

                        // Loading or Prompt Overlay
                        when (val state = tryOnState) {
                            is AsyncUiState.Loading -> {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFFDCC08A),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Synthesizing 3D Drape ($activeAngle)...",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFFAF6F0)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Simulating fabric physics & skin-tone lighting (30–60s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE5DDD0)
                                    )
                                }
                            }
                            is AsyncUiState.Idle -> {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(18.dp)
                                ) {
                                    Text(
                                        text = "3D Couture Fitting Stage",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color(0xFFFAF6F0)
                                    )
                                    Text(
                                        text = if (selectedItems.isNotEmpty()) {
                                            "Ready to drape ${selectedItems.size} selected wardrobe pieces on your ${profile.bodyType} model."
                                        } else {
                                            "Select wardrobe items or describe a look below to render a 3D try-on."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE5DDD0)
                                    )
                                }
                            }
                            is AsyncUiState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(14.dp)
                                ) {
                                    AtelierErrorCard(
                                        message = state.message,
                                        onRetry = {
                                            viewModel.render3dVirtualTryOn(customOutfitText, tryOnOccasion, activeAngle)
                                        }
                                    )
                                }
                            }
                            is AsyncUiState.Success -> {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AtelierObsidian.copy(alpha = 0.82f)
                                    ) {
                                        Row(modifier = Modifier.padding(4.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.saveRenderedTryOnAsOutfit(
                                                        title = selectedItems.firstOrNull()?.name?.let { "$it Studio Look" }
                                                            ?: "$tryOnOccasion 3D Look",
                                                        occasion = tryOnOccasion
                                                    )
                                                },
                                                border = BorderStroke(1.dp, Color(0xFFDCC08A)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFAF6F0))
                                            ) {
                                                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Save")
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Button(
                                                onClick = {
                                                    SocialShareHelper.shareOutfitLook(
                                                        context = context,
                                                        outfitTitle = "Atelier 3D Try-On ($tryOnOccasion)",
                                                        occasion = tryOnOccasion,
                                                        stylingNotes = "Rendered for ${profile.bodyType} silhouette • ${profile.skinTone} (${profile.skinUndertone}) palette.",
                                                        bitmap = state.data
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFFDCC08A),
                                                    contentColor = AtelierObsidian
                                                ),
                                                modifier = Modifier.testTag("share_3d_tryon_button")
                                            ) {
                                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Share")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Multi-Angle 3D Camera Controls
            item {
                Text("3D Camera Angle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AtelierConstants.TRY_ON_ANGLES.forEach { angle ->
                        FilterChip(
                            selected = activeAngle == angle,
                            onClick = { viewModel.setTryOnAngle(angle) },
                            label = { Text(angle) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Outfit Selection & Custom Description Controls
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Garments on 3D Model",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (selectedItems.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                selectedItems.forEach { item ->
                                    AtelierTagPill(
                                        text = "${item.colorName} ${item.name}",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = customOutfitText,
                                onValueChange = { customOutfitText = it },
                                label = { Text("Describe outfit (or select from Wardrobe)") },
                                placeholder = { Text("e.g. Ivory silk bandhgala jacket with charcoal pleated trousers") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tryon_outfit_description_input")
                            )
                        }

                        Text("Occasion Context", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(AtelierConstants.OCCASIONS) { occ ->
                                FilterChip(
                                    selected = tryOnOccasion == occ,
                                    onClick = { tryOnOccasion = occ },
                                    label = { Text(occ) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.render3dVirtualTryOn(
                                    customOutfitPrompt = customOutfitText,
                                    occasion = tryOnOccasion,
                                    angle = activeAngle
                                )
                            },
                            enabled = tryOnState !is AsyncUiState.Loading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onBackground,
                                contentColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("render_3d_tryon_button")
                        ) {
                            Icon(Icons.Outlined.ViewInAr, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Render High-Fidelity 3D Try-On ($activeAngle)")
                        }
                    }
                }
            }
        } else {
            // FULL BODY STRUCTURE SCAN & SKIN TONE MATCH STUDIO
            item {
                BodyStructureScanPanel(
                    profile = profile,
                    bodyScanState = bodyScanState,
                    onRunAiBodyScan = { bitmap, path ->
                        viewModel.runFullBodyStructureScan(bitmap, path)
                    },
                    onSaveMeasurements = { bodyType, h, c, w, hip, sh, tone, undertone ->
                        viewModel.saveManualBodyMeasurements(bodyType, h, c, w, hip, sh, tone, undertone)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyStructureScanPanel(
    profile: UserStyleProfile,
    bodyScanState: AsyncUiState<com.example.data.model.BodyScanAnalysisResult>,
    onRunAiBodyScan: (Bitmap?, String?) -> Unit,
    onSaveMeasurements: (String, Int, Int, Int, Int, Int, String, String) -> Unit
) {
    val context = LocalContext.current
    var bodyType by remember(profile.bodyType) { mutableStateOf(profile.bodyType) }
    var heightCm by remember(profile.heightCm) { mutableIntStateOf(profile.heightCm.coerceIn(135, 215)) }
    var chestCm by remember(profile.chestCm) { mutableIntStateOf(profile.chestCm.coerceIn(70, 145)) }
    var waistCm by remember(profile.waistCm) { mutableIntStateOf(profile.waistCm.coerceIn(55, 135)) }
    var hipCm by remember(profile.hipCm) { mutableIntStateOf(profile.hipCm.coerceIn(75, 150)) }
    var shoulderCm by remember(profile.shoulderCm) { mutableIntStateOf(profile.shoulderCm.coerceIn(34, 62)) }
    var skinTone by remember(profile.skinTone) { mutableStateOf(profile.skinTone) }
    var skinUndertone by remember(profile.skinUndertone) { mutableStateOf(profile.skinUndertone) }

    var scannedPhotoPath by remember(profile.bodyScanLocalPath) { mutableStateOf(profile.bodyScanLocalPath) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun handleBodyPhoto(uri: Uri) {
        val persisted = ImageStorageHelper.persistCompressedImage(
            context = context,
            sourceUri = uri,
            subDir = "body",
            filePrefix = "body_scan"
        )
        if (persisted != null) {
            scannedPhotoPath = persisted.first
            onRunAiBodyScan(persisted.second, persisted.first)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) handleBodyPhoto(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) handleBodyPhoto(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, _) = ImageStorageHelper.createCameraCaptureUri(context, "body_scan")
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // AI Vision Full Body Structure Scan Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.PersonSearch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Full Body Structure & Skin Tone Scan", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "Capture or pick a well-lit full-body photo for AI proportion mapping, precise garment fit notes, and personalized skin-tone color swatches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (scannedPhotoPath.isNotBlank() && File(scannedPhotoPath).exists()) {
                    AsyncImage(
                        model = File(scannedPhotoPath),
                        contentDescription = "Body scan reference",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                val (uri, _) = ImageStorageHelper.createCameraCaptureUri(context, "body_scan")
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("camera_body_scan_button")
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Camera Scan")
                    }
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("gallery_body_scan_button")
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photo Picker")
                    }
                }

                OutlinedButton(
                    onClick = { onRunAiBodyScan(null, scannedPhotoPath.ifBlank { null }) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("analyze_measurements_button")
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run AI Fit & Skin-Tone Analysis from Measurements")
                }

                when (val s = bodyScanState) {
                    is AsyncUiState.Loading -> AtelierAiLoadingCard(
                        title = "Scanning Proportions & Undertone...",
                        subtitle = "Computing body silhouette, flattering cuts, and seasonal color palette."
                    )
                    is AsyncUiState.Error -> AtelierErrorCard(message = s.message)
                    else -> Unit
                }
            }
        }

        // Skin Tone Palette & Fit Readout
        if (profile.fitSummary.isNotBlank() || profile.bestColors.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Your Tailoring & Color Match", style = MaterialTheme.typography.titleMedium)
                    }
                    if (profile.fitSummary.isNotBlank()) {
                        Text(
                            text = profile.fitSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (profile.bodyAnalysisNotes.isNotBlank()) {
                        Text(
                            text = profile.bodyAnalysisNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("Most Flattering Shades (${profile.skinTone} • ${profile.skinUndertone})", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        profile.bestColors.forEach { shade ->
                            AtelierTagPill(
                                text = shade,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    if (profile.avoidColors.isNotEmpty()) {
                        Text("Shades to Use Sparingly", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            profile.avoidColors.forEach { shade ->
                                AtelierTagPill(text = shade)
                            }
                        }
                    }
                }
            }
        }

        // Precision Anthropometric Sliders
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Straighten, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Precision Measurements & Undertone", style = MaterialTheme.typography.titleMedium)
                }

                Text("Body Structure Archetype", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AtelierConstants.BODY_TYPES) { type ->
                        FilterChip(
                            selected = bodyType == type,
                            onClick = { bodyType = type },
                            label = { Text(type) }
                        )
                    }
                }

                MeasurementSliderRow("Height", heightCm, 135..215, "cm") { heightCm = it }
                MeasurementSliderRow("Shoulder Width", shoulderCm, 34..62, "cm") { shoulderCm = it }
                MeasurementSliderRow("Chest / Bust", chestCm, 70..145, "cm") { chestCm = it }
                MeasurementSliderRow("Waist", waistCm, 55..135, "cm") { waistCm = it }
                MeasurementSliderRow("Hip", hipCm, 75..150, "cm") { hipCm = it }

                Text("Skin Tone", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AtelierConstants.SKIN_TONES) { tone ->
                        FilterChip(
                            selected = skinTone == tone,
                            onClick = { skinTone = tone },
                            label = { Text(tone) }
                        )
                    }
                }

                Text("Undertone", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AtelierConstants.SKIN_UNDERTONES.forEach { ut ->
                        FilterChip(
                            selected = skinUndertone == ut,
                            onClick = { skinUndertone = ut },
                            label = { Text(ut) }
                        )
                    }
                }

                Button(
                    onClick = {
                        onSaveMeasurements(bodyType, heightCm, chestCm, waistCm, hipCm, shoulderCm, skinTone, skinUndertone)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_body_measurements_button")
                ) {
                    Text("Save Fit Profile")
                }
            }
        }
    }
}

@Composable
private fun MeasurementSliderRow(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    onChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$value $unit",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}
