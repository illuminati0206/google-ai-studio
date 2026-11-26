package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AiOutfitRecommendation
import com.example.data.model.AsyncUiState
import com.example.data.model.AtelierConstants
import com.example.data.model.PlannedOutfitEvent
import com.example.data.model.UserStyleProfile
import com.example.data.update.PlayUpdateUiState
import com.example.data.weather.WeatherForecastSnapshot
import com.example.domain.CompatibleOutfitProposal
import com.example.ui.components.AtelierAiLoadingCard
import com.example.ui.components.AtelierErrorCard
import com.example.ui.components.AtelierTagPill
import com.example.ui.components.ColorSwatchDot
import com.example.ui.components.EditorialSectionHeader
import com.example.ui.components.HomeUpdateAvailableBanner
import com.example.ui.components.PlayAndRemoteConfigUpdateBanner
import com.example.ui.theme.AtelierObsidian
import com.example.ui.viewmodel.AtelierViewModel
import com.example.util.SocialShareHelper

@Composable
fun HomeScreen(
    viewModel: AtelierViewModel,
    playUpdateState: PlayUpdateUiState,
    onOpenProfileSheet: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onStartFlexiblePlayUpdate: () -> Unit,
    onStartImmediatePlayUpdate: () -> Unit,
    onCompleteFlexiblePlayUpdate: () -> Unit,
    onNavigateToWardrobe: () -> Unit,
    onNavigateToStudio: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToPlanner: () -> Unit
) {
    val context = LocalContext.current
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val wardrobeState by viewModel.wardrobeState.collectAsStateWithLifecycle()
    val outfitsState by viewModel.savedOutfitsState.collectAsStateWithLifecycle()
    val eventsState by viewModel.plannedEventsState.collectAsStateWithLifecycle()
    val selectedOccasion by viewModel.selectedOccasion.collectAsStateWithLifecycle()
    val curationsState by viewModel.occasionCurationsState.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val weatherCityQuery by viewModel.weatherCityQuery.collectAsStateWithLifecycle()
    val weatherOutfits by viewModel.weatherSuggestedOutfits.collectAsStateWithLifecycle()
    val remoteConfigUpdateState by viewModel.remoteConfigUpdateState.collectAsStateWithLifecycle()
    val remoteConfigBannerDismissed by viewModel.remoteConfigBannerDismissed.collectAsStateWithLifecycle()
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val updateBannerDismissed by viewModel.updateBannerDismissed.collectAsStateWithLifecycle()

    val profile = (profileState as? AsyncUiState.Success)?.data ?: UserStyleProfile()
    val wardrobeCount = (wardrobeState as? AsyncUiState.Success)?.data?.size ?: 0
    val savedCount = (outfitsState as? AsyncUiState.Success)?.data?.size ?: 0
    val nextEvent = (eventsState as? AsyncUiState.Success)?.data?.firstOrNull()
    val remoteConfigInfo = (remoteConfigUpdateState as? AsyncUiState.Success)?.data
    val availableRelease = (updateCheckState as? AsyncUiState.Success)?.data?.takeIf { it.isUpdateAvailable }
    val hasAnyUpdateAvailable = (remoteConfigInfo?.isUpdateAvailable == true) ||
        playUpdateState is PlayUpdateUiState.UpdateAvailable ||
        playUpdateState is PlayUpdateUiState.Downloading ||
        playUpdateState is PlayUpdateUiState.Downloaded ||
        availableRelease != null

    var showCityPickerDialog by remember { mutableStateOf(false) }

    val coarseLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.fetchWeatherFromDeviceLocation()
        } else {
            showCityPickerDialog = true
        }
    }

    fun requestLocalWeather() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.fetchWeatherFromDeviceLocation()
        } else {
            coarseLocationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Editorial Masthead & Quick Gender / Style Switcher
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "ATELIER • ${profile.gender.uppercase()} EDITION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Hello, ${profile.displayName.ifBlank { "Stylist" }.substringBefore(" ")}",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (hasAnyUpdateAvailable) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (hasAnyUpdateAvailable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenUpdateDialog() }
                            .testTag("open_github_updates_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.SystemUpdate,
                                contentDescription = "Check app updates",
                                tint = if (hasAnyUpdateAvailable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenProfileSheet() }
                            .testTag("open_profile_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Edit style profile and gender",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Firebase Remote Config & Google Play In-App Updates Banner
        val showPlayOrRemoteBanner = (playUpdateState is PlayUpdateUiState.Downloading ||
            playUpdateState is PlayUpdateUiState.Downloaded) ||
            (!remoteConfigBannerDismissed && (remoteConfigInfo?.isUpdateAvailable == true || playUpdateState is PlayUpdateUiState.UpdateAvailable))

        if (showPlayOrRemoteBanner) {
            item {
                PlayAndRemoteConfigUpdateBanner(
                    remoteConfigInfo = remoteConfigInfo,
                    playUpdateState = playUpdateState,
                    onStartFlexibleUpdate = onStartFlexiblePlayUpdate,
                    onStartImmediateUpdate = onStartImmediatePlayUpdate,
                    onCompleteFlexibleUpdate = onCompleteFlexiblePlayUpdate,
                    onOpenDetails = onOpenUpdateDialog,
                    onDismiss = { viewModel.dismissRemoteConfigBanner() }
                )
            }
        }

        // GitHub Release Update Banner (shown automatically when newer release exists on GitHub)
        if (availableRelease != null && !updateBannerDismissed) {
            item {
                HomeUpdateAvailableBanner(
                    release = availableRelease,
                    onOpenDownload = { url -> viewModel.openGitHubUpdateUrl(url) },
                    onViewDetails = onOpenUpdateDialog,
                    onDismiss = { viewModel.dismissUpdateBanner() }
                )
            }
        }

        // Gender Quick Selector Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AtelierConstants.GENDERS.forEach { genderOption ->
                    val isSelected = profile.gender == genderOption
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                viewModel.saveStylePreferences(
                                    displayName = profile.displayName,
                                    gender = genderOption,
                                    stylePreferences = profile.stylePreferences,
                                    budgetTier = profile.budgetTier
                                )
                            }
                        },
                        label = {
                            Text(
                                text = genderOption,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                            selectedLabelColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier.testTag("gender_chip_${genderOption.lowercase()}")
                    )
                }
            }
        }

        // Hero Studio Editorial Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onNavigateToStudio() }
                    .testTag("hero_studio_card")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_atelier_hero),
                    contentDescription = "Atelier 3D virtual try-on hero",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    AtelierObsidian.copy(alpha = 0.88f),
                                    AtelierObsidian.copy(alpha = 0.45f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    AtelierTagPill(
                        text = "${profile.bodyType} • ${profile.skinTone} (${profile.skinUndertone})",
                        containerColor = Color(0xFFFAF6F0).copy(alpha = 0.16f),
                        contentColor = Color(0xFFFAF6F0),
                        icon = Icons.Outlined.Person
                    )
                    Column {
                        Text(
                            text = "3D Fit & Try-On Studio",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFFAF6F0)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan body structure & preview exact drape in 3D before stepping out.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE7DFD5)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AtelierTagPill(
                                text = "$wardrobeCount Wardrobe Pieces",
                                containerColor = Color(0xFFDCC08A),
                                contentColor = AtelierObsidian,
                                icon = Icons.Outlined.Checkroom
                            )
                            AtelierTagPill(
                                text = "$savedCount Saved Looks",
                                containerColor = Color(0xFFFAF6F0).copy(alpha = 0.2f),
                                contentColor = Color(0xFFFAF6F0),
                                icon = Icons.Outlined.ViewInAr
                            )
                        }
                    }
                }
            }
        }

        // Live Local Weather & Weather-Adjusted Wardrobe Recommendations
        item {
            LiveWeatherStylingCard(
                weatherState = weatherState,
                wardrobeCount = wardrobeCount,
                weatherOutfits = weatherOutfits,
                onUseLocalGps = { requestLocalWeather() },
                onChangeCity = { showCityPickerDialog = true },
                onOpenWardrobe = onNavigateToWardrobe,
                onTryOutfitIn3d = { proposal ->
                    viewModel.selectMixMatchSet(proposal.items.map { it.id })
                    viewModel.render3dVirtualTryOn(
                        customOutfitPrompt = "",
                        occasion = selectedOccasion
                    )
                    onNavigateToStudio()
                }
            )
        }

        // Next Scheduled Event Banner (if planned)
        if (nextEvent != null) {
            item {
                NextPlannedEventCard(
                    event = nextEvent,
                    onClick = onNavigateToPlanner
                )
            }
        }

        // Quick Action Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickAtelierTile(
                    title = "Upload & Mix",
                    subtitle = "Closet & Shoes",
                    icon = Icons.Outlined.Checkroom,
                    onClick = onNavigateToWardrobe,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_wardrobe_tile")
                )
                QuickAtelierTile(
                    title = "Style Upgrades",
                    subtitle = "Eco & Deals",
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = onNavigateToDiscover,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_discover_tile")
                )
            }
        }

        // Occasion-Based AI Styling Curations
        item {
            EditorialSectionHeader(
                eyebrow = "OCCASION CURATIONS",
                title = "Styled for the Moment",
                actionLabel = "Curate Now",
                onActionClick = { viewModel.selectOccasionAndCurate(selectedOccasion, forceRefresh = true) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AtelierConstants.OCCASIONS) { occasion ->
                    val selected = occasion == selectedOccasion
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectOccasionAndCurate(occasion) },
                        label = { Text(occasion, style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("occasion_chip_${occasion.take(8).lowercase()}")
                    )
                }
            }
        }

        item {
            when (val state = curationsState) {
                is AsyncUiState.Idle -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "AI Stylist Ready for $selectedOccasion",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Generate 3 trend-aligned looks combining your virtual wardrobe, ${profile.bodyType} structure, and ${profile.skinTone} (${profile.skinUndertone}) palette.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.selectOccasionAndCurate(selectedOccasion) },
                                modifier = Modifier.testTag("curate_occasion_button")
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Curate $selectedOccasion Looks")
                            }
                        }
                    }
                }
                is AsyncUiState.Loading -> {
                    AtelierAiLoadingCard(
                        title = "Curating $selectedOccasion Looks...",
                        subtitle = "Matching your wardrobe, ${profile.bodyType} silhouette, skin undertone & 2026 fashion trends."
                    )
                }
                is AsyncUiState.Error -> {
                    AtelierErrorCard(
                        message = state.message,
                        onRetry = { viewModel.selectOccasionAndCurate(selectedOccasion, forceRefresh = true) }
                    )
                }
                is AsyncUiState.Success -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.data.forEach { rec ->
                            AiCuratedLookCard(
                                recommendation = rec,
                                onSave = { viewModel.saveAiOutfitToLookbook(rec) },
                                onTryIn3d = {
                                    viewModel.render3dVirtualTryOn(
                                        customOutfitPrompt = "${rec.title}: ${rec.itemsUsed.joinToString(", ")}",
                                        occasion = rec.occasion
                                    )
                                    onNavigateToStudio()
                                },
                                onShare = {
                                    SocialShareHelper.shareOutfitLook(
                                        context = context,
                                        outfitTitle = rec.title,
                                        occasion = rec.occasion,
                                        stylingNotes = "${rec.itemsUsed.joinToString(" • ")}\n${rec.colorTheoryNote}\n${rec.bodyFitAdvice}"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCityPickerDialog) {
        WeatherCityPickerDialog(
            initialCity = weatherCityQuery,
            onDismiss = { showCityPickerDialog = false },
            onSelectCity = { city ->
                viewModel.fetchWeatherForCity(city)
                showCityPickerDialog = false
            }
        )
    }
}

@Composable
private fun LiveWeatherStylingCard(
    weatherState: AsyncUiState<WeatherForecastSnapshot>,
    wardrobeCount: Int,
    weatherOutfits: List<CompatibleOutfitProposal>,
    onUseLocalGps: () -> Unit,
    onChangeCity: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onTryOutfitIn3d: (CompatibleOutfitProposal) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_weather_card"),
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
                Column {
                    Text(
                        text = "LOCAL WEATHER STYLING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Forecast-Matched Closet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onUseLocalGps,
                        modifier = Modifier.testTag("weather_gps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MyLocation,
                            contentDescription = "Use local location for weather",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = onChangeCity,
                        modifier = Modifier.testTag("weather_change_city_button")
                    ) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("City")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (weatherState) {
                is AsyncUiState.Loading, is AsyncUiState.Idle -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Fetching live temperature & precipitation forecast...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is AsyncUiState.Error -> {
                    Text(
                        text = weatherState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onChangeCity) {
                        Text("Choose City")
                    }
                }
                is AsyncUiState.Success -> {
                    val wx = weatherState.data
                    val weatherIcon = when {
                        wx.isRainLikely -> Icons.Outlined.WaterDrop
                        wx.weatherCode == 0 -> Icons.Outlined.WbSunny
                        else -> Icons.Outlined.Cloud
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = weatherIcon,
                                    contentDescription = wx.conditionLabel,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column {
                                Text(
                                    text = "${wx.currentTempC}°C • ${wx.conditionLabel}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${wx.locationName} • Feels ${wx.feelsLikeC}°C",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AtelierTagPill(
                            text = "${wx.precipitationProbPercent}% Rain",
                            containerColor = if (wx.isRainLikely) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (wx.isRainLikely) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = Icons.Outlined.WaterDrop
                        )
                        AtelierTagPill(
                            text = "${wx.windKmh} km/h Wind",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = Icons.Outlined.Air
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    val fabricAdvice = when {
                        wx.isRainLikely -> "Recommended fabrics today: Leather shoes & Synthetic Blend or structured layers. Avoid delicate raw Silk."
                        wx.currentTempC >= 28 -> "Recommended fabrics today: Ultra-breathable Linen & Cotton in light tones."
                        wx.currentTempC <= 15 -> "Recommended fabrics today: Insulating Wool, Cashmere, or Velvet layers with enclosed shoes."
                        else -> "Recommended fabrics today: Balanced Cotton, Silk, or mid-weight Denim."
                    }
                    Text(
                        text = fabricAdvice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    if (weatherOutfits.isNotEmpty()) {
                        Text(
                            text = "SUGGESTED FROM YOUR CLOSET FOR ${wx.currentTempC}°C",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(weatherOutfits) { proposal ->
                                Surface(
                                    modifier = Modifier.width(236.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.background,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AtelierTagPill(
                                                text = "Comfort ${proposal.materialComfortScore}%",
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                proposal.items.take(4).forEach {
                                                    ColorSwatchDot(hexColor = it.colorHex, sizeDp = 12)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = proposal.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = proposal.items.joinToString(" + ") { it.name },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { onTryOutfitIn3d(proposal) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Outlined.ViewInAr, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Preview in 3D")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onOpenWardrobe,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Checkroom, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (wardrobeCount < 2) "Upload 2+ Closet Items for Weather Outfits"
                                else "Open Closet & Material Filters"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherCityPickerDialog(
    initialCity: String,
    onDismiss: () -> Unit,
    onSelectCity: (String) -> Unit
) {
    var cityInput by remember { mutableStateOf(initialCity) }
    val quickCities = listOf("New Delhi", "Mumbai", "Bengaluru", "London", "New York", "Paris")

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        title = { Text("Set Weather Location", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Weather forecasts automatically tune material comfort scoring and Event Planner recommendations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = { cityInput = it },
                    label = { Text("City Name") },
                    placeholder = { Text("e.g. Mumbai, Jaipur, London") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weather_city_input")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickCities) { preset ->
                        FilterChip(
                            selected = cityInput.equals(preset, ignoreCase = true),
                            onClick = { cityInput = preset },
                            label = { Text(preset) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelectCity(cityInput) },
                modifier = Modifier.testTag("confirm_weather_city_button")
            ) {
                Text("Update Forecast")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun QuickAtelierTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun NextPlannedEventCard(
    event: PlannedOutfitEvent,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarToday,
                contentDescription = "Upcoming outfit event",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UPCOMING STYLING SESSION • ${event.dateIso} ${event.timeLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = event.eventTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (event.outfitTitle.isNotBlank()) {
                    Text(
                        text = "Look: ${event.outfitTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AiCuratedLookCard(
    recommendation: AiOutfitRecommendation,
    onSave: () -> Unit,
    onTryIn3d: () -> Unit,
    onShare: () -> Unit
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
                AtelierTagPill(
                    text = recommendation.trendTag,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${recommendation.harmonyScore}% Match",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share look",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = recommendation.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))
            recommendation.itemsUsed.forEach { piece ->
                Text(
                    text = "• $piece",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (recommendation.colorTheoryNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Color & Skin Match: ${recommendation.colorTheoryNote} ${recommendation.skinToneNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (recommendation.bodyFitAdvice.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tailoring Fit: ${recommendation.bodyFitAdvice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("3D Try-On")
                }
            }
        }
    }
}
