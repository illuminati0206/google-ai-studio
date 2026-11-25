package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AsyncUiState
import com.example.data.model.AtelierConstants
import com.example.data.model.PlannedOutfitEvent
import com.example.data.model.SavedOutfit
import com.example.data.model.UserStyleProfile
import com.example.data.model.WardrobeItem
import com.example.data.weather.WeatherForecastSnapshot
import com.example.domain.EventWeatherAdjustment
import com.example.domain.WardrobeCompatibilityEngine
import com.example.ui.components.AtelierEmptyStateCard
import com.example.ui.components.AtelierTagPill
import com.example.ui.components.EditorialSectionHeader
import com.example.ui.viewmodel.AtelierViewModel
import com.example.util.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun PlannerScreen(
    viewModel: AtelierViewModel
) {
    val context = LocalContext.current
    val eventsState by viewModel.plannedEventsState.collectAsStateWithLifecycle()
    val outfitsState by viewModel.savedOutfitsState.collectAsStateWithLifecycle()
    val wardrobeState by viewModel.wardrobeState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val weatherCityQuery by viewModel.weatherCityQuery.collectAsStateWithLifecycle()

    val events = (eventsState as? AsyncUiState.Success)?.data.orEmpty()
    val savedOutfits = (outfitsState as? AsyncUiState.Success)?.data.orEmpty()
    val wardrobeItems = (wardrobeState as? AsyncUiState.Success)?.data.orEmpty()
    val profile = (profileState as? AsyncUiState.Success)?.data ?: UserStyleProfile()
    val weatherSnapshot = (weatherState as? AsyncUiState.Success)?.data

    val initialCal = remember { Calendar.getInstance() }
    var displayYear by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var displayMonth by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }
    var selectedDateIso by remember {
        mutableStateOf(
            String.format(
                Locale.US,
                "%04d-%02d-%02d",
                initialCal.get(Calendar.YEAR),
                initialCal.get(Calendar.MONTH) + 1,
                initialCal.get(Calendar.DAY_OF_MONTH)
            )
        )
    }

    var showScheduleDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.fetchWeatherFromDeviceLocation()
        } else {
            viewModel.fetchWeatherForCity(weatherCityQuery)
        }
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun syncPlannerWeather() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.fetchWeatherFromDeviceLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val datesWithEvents = remember(events) { events.map { it.dateIso }.toSet() }
    val selectedDayEvents = remember(events, selectedDateIso) {
        events.filter { it.dateIso == selectedDateIso }
    }
    val selectedDateForecast = remember(weatherSnapshot, selectedDateIso) {
        weatherSnapshot?.forecastForDate(selectedDateIso)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            EditorialSectionHeader(
                eyebrow = "EVENT OUTFIT PLANNER",
                title = "Styling Calendar",
                actionLabel = "+ Schedule Look",
                onActionClick = {
                    ensureNotificationPermission()
                    showScheduleDialog = true
                }
            )
        }

        // Live Weather Sync Banner for Calendar Auto-Adjustments
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("planner_weather_sync_card"),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val icon = when {
                        weatherSnapshot?.isRainLikely == true -> Icons.Outlined.WaterDrop
                        weatherSnapshot?.weatherCode == 0 -> Icons.Outlined.WbSunny
                        else -> Icons.Outlined.Cloud
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Calendar weather sync",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (weatherSnapshot != null) {
                                "${weatherSnapshot.locationName} • ${weatherSnapshot.currentTempC}°C (${weatherSnapshot.conditionLabel})"
                            } else {
                                "Weather Auto-Adjust Ready"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedDateForecast != null) {
                                "$selectedDateIso forecast: ${selectedDateForecast.tempMinC}°–${selectedDateForecast.tempMaxC}°C • ${selectedDateForecast.precipitationProbPercent}% rain"
                            } else {
                                "14-day forecast auto-adjusts outfit materials & layers on planned events."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { syncPlannerWeather() },
                        modifier = Modifier.testTag("planner_sync_weather_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MyLocation,
                            contentDescription = "Refresh local weather forecast",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Interactive Monthly Calendar Card
        item {
            MonthlyOutfitCalendarCard(
                year = displayYear,
                month = displayMonth,
                selectedDateIso = selectedDateIso,
                datesWithEvents = datesWithEvents,
                onPrevMonth = {
                    if (displayMonth == 0) {
                        displayMonth = 11
                        displayYear -= 1
                    } else {
                        displayMonth -= 1
                    }
                },
                onNextMonth = {
                    if (displayMonth == 11) {
                        displayMonth = 0
                        displayYear += 1
                    } else {
                        displayMonth += 1
                    }
                },
                onSelectDate = { selectedDateIso = it }
            )
        }

        // Quick Action Bar for Selected Date + 24h Reminder Notification Test
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        ensureNotificationPermission()
                        showScheduleDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("schedule_event_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Plan $selectedDateIso")
                }
                OutlinedButton(
                    onClick = {
                        ensureNotificationPermission()
                        val sample = selectedDayEvents.firstOrNull() ?: events.firstOrNull()
                        ReminderScheduler.triggerInstantTestNotification(
                            context = context,
                            title = "24h Styling Reminder: ${sample?.eventTitle ?: "Upcoming Event"}",
                            body = if (sample?.outfitTitle?.isNotBlank() == true) {
                                "Your scheduled look \"${sample.outfitTitle}\" is set for tomorrow (${sample.dateIso} ${sample.timeLabel}). Steam & prep accessories tonight."
                            } else {
                                "Your styling session reminder is active. Open Atelier to review tomorrow's outfit."
                            }
                        )
                    },
                    modifier = Modifier.testTag("test_reminder_notification_button")
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Alert")
                }
            }
        }

        // Selected Date Agenda
        item {
            Text(
                text = "PLANNED FOR $selectedDateIso (${selectedDayEvents.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (selectedDayEvents.isEmpty()) {
            item {
                AtelierEmptyStateCard(
                    title = "No Styling Session on $selectedDateIso",
                    subtitle = "Schedule weddings, office presentations, or evenings out and attach an outfit from your wardrobe with automatic weather adjustments & 24-hour reminders.",
                    actionLabel = "Schedule Outfit Event",
                    onAction = {
                        ensureNotificationPermission()
                        showScheduleDialog = true
                    }
                )
            }
        } else {
            items(selectedDayEvents, key = { it.id }) { event ->
                val adjustment = remember(event, weatherSnapshot, wardrobeItems, profile) {
                    weatherSnapshot?.forecastForDate(event.dateIso)?.let { forecast ->
                        WardrobeCompatibilityEngine.evaluateEventWeatherAdjustment(
                            forecast = forecast,
                            occasion = event.occasion,
                            wardrobe = wardrobeItems,
                            profile = profile
                        )
                    }
                }
                PlannedEventCard(
                    event = event,
                    weatherAdjustment = adjustment,
                    onApplyWeatherAdjustment = { adj ->
                        viewModel.applyWeatherAdjustedOutfitToEvent(event, adj)
                    },
                    onToggleReminder = { enabled ->
                        if (enabled) ensureNotificationPermission()
                        viewModel.toggleEventReminder(event, enabled)
                    },
                    onDelete = { viewModel.deletePlannedEvent(event.id) }
                )
            }
        }

        // All Upcoming Scheduled Looks
        if (events.isNotEmpty()) {
            item {
                EditorialSectionHeader(
                    eyebrow = "FULL AGENDA",
                    title = "All Scheduled Outfits (${events.size})"
                )
            }
            items(events, key = { "all_${it.id}" }) { event ->
                val adjustment = remember(event, weatherSnapshot, wardrobeItems, profile) {
                    weatherSnapshot?.forecastForDate(event.dateIso)?.let { forecast ->
                        WardrobeCompatibilityEngine.evaluateEventWeatherAdjustment(
                            forecast = forecast,
                            occasion = event.occasion,
                            wardrobe = wardrobeItems,
                            profile = profile
                        )
                    }
                }
                PlannedEventCard(
                    event = event,
                    weatherAdjustment = adjustment,
                    onApplyWeatherAdjustment = { adj ->
                        viewModel.applyWeatherAdjustedOutfitToEvent(event, adj)
                    },
                    onToggleReminder = { enabled ->
                        if (enabled) ensureNotificationPermission()
                        viewModel.toggleEventReminder(event, enabled)
                    },
                    onDelete = { viewModel.deletePlannedEvent(event.id) }
                )
            }
        }
    }

    if (showScheduleDialog) {
        ScheduleEventDialog(
            initialDateIso = selectedDateIso,
            savedOutfits = savedOutfits,
            wardrobeItems = wardrobeItems,
            profile = profile,
            weatherSnapshot = weatherSnapshot,
            onDismiss = { showScheduleDialog = false },
            onSchedule = { title, occasion, dateIso, timeLabel, outfit, customWardrobeSummary, leadMinutes, reminderEnabled, notes ->
                val resolvedOutfit = outfit ?: if (customWardrobeSummary.isNotBlank()) {
                    SavedOutfit(id = "", title = customWardrobeSummary)
                } else {
                    null
                }
                viewModel.schedulePlannedOutfitEvent(
                    eventTitle = title,
                    occasion = occasion,
                    dateIso = dateIso,
                    timeLabel = timeLabel,
                    outfit = resolvedOutfit,
                    reminderLeadMinutes = leadMinutes,
                    reminderEnabled = reminderEnabled,
                    notes = notes
                )
                showScheduleDialog = false
            }
        )
    }
}

@Composable
private fun MonthlyOutfitCalendarCard(
    year: Int,
    month: Int,
    selectedDateIso: String,
    datesWithEvents: Set<String>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (String) -> Unit
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val monthTitle = remember(year, month) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7

    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                IconButton(onClick = onPrevMonth) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            for (week in 0 until (totalCells / 7)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (dow in 0..6) {
                        val cellIndex = week * 7 + dow
                        val dayNum = cellIndex - firstDayOfWeek + 1
                        if (dayNum in 1..daysInMonth) {
                            val iso = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayNum)
                            val isSelected = iso == selectedDateIso
                            val hasEvent = iso in datesWithEvents
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onBackground
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { onSelectDate(iso) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$dayNum",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hasEvent) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.secondary
                                                    else MaterialTheme.colorScheme.primary
                                                )
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedEventCard(
    event: PlannedOutfitEvent,
    weatherAdjustment: EventWeatherAdjustment?,
    onApplyWeatherAdjustment: (EventWeatherAdjustment) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val leadText = when (event.reminderLeadMinutes) {
        1440 -> "24h before"
        720 -> "12h before"
        120 -> "2h before"
        else -> "${event.reminderLeadMinutes}m before"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AtelierTagPill(
                            text = event.occasion,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${event.dateIso} • ${event.timeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = event.eventTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (event.outfitTitle.isNotBlank()) {
                        Text(
                            text = "Assigned Look: ${event.outfitTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (event.notes.isNotBlank()) {
                        Text(
                            text = event.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (event.reminderEnabled) "Reminder active ($leadText)" else "Reminder paused",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (event.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = event.reminderEnabled, onCheckedChange = onToggleReminder)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete scheduled event")
                }
            }

            if (weatherAdjustment != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (weatherAdjustment.forecast.isRainLikely) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (weatherAdjustment.forecast.isRainLikely) Icons.Outlined.WaterDrop else Icons.Outlined.WbSunny,
                                contentDescription = "Event weather forecast",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = weatherAdjustment.advisoryHeadline,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = weatherAdjustment.adjustmentTip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Best Materials: ${weatherAdjustment.recommendedMaterials.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val proposal = weatherAdjustment.suggestedWardrobeOutfit
                        if (proposal != null) {
                            Text(
                                text = "Closet Match (${proposal.materialComfortScore}% comfort): ${proposal.items.joinToString(" + ") { it.name }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        OutlinedButton(
                            onClick = { onApplyWeatherAdjustment(weatherAdjustment) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auto_adjust_event_${event.id}")
                        ) {
                            Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (proposal != null) "Auto-Adjust Look for Weather" else "Apply Weather Advisory Notes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEventDialog(
    initialDateIso: String,
    savedOutfits: List<SavedOutfit>,
    wardrobeItems: List<WardrobeItem>,
    profile: UserStyleProfile,
    weatherSnapshot: WeatherForecastSnapshot?,
    onDismiss: () -> Unit,
    onSchedule: (String, String, String, String, SavedOutfit?, String, Int, Boolean, String) -> Unit
) {
    var eventTitle by remember { mutableStateOf("") }
    var occasion by remember { mutableStateOf(AtelierConstants.OCCASIONS.first()) }
    var dateIso by remember { mutableStateOf(initialDateIso) }
    var timeLabel by remember { mutableStateOf("18:30") }
    var selectedOutfit by remember { mutableStateOf(savedOutfits.firstOrNull()) }
    var selectedWardrobeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reminderLeadMinutes by remember { mutableIntStateOf(1440) }
    var reminderEnabled by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    val forecastAdjustment = remember(dateIso, occasion, weatherSnapshot, wardrobeItems, profile) {
        weatherSnapshot?.forecastForDate(dateIso.trim())?.let { f ->
            WardrobeCompatibilityEngine.evaluateEventWeatherAdjustment(
                forecast = f,
                occasion = occasion,
                wardrobe = wardrobeItems,
                profile = profile
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        title = { Text("Schedule Outfit Event", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = eventTitle,
                    onValueChange = { eventTitle = it },
                    label = { Text("Event Name") },
                    placeholder = { Text("e.g. Sister's Wedding, Office Presentation") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("event_title_input")
                )

                Text("Occasion Category", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AtelierConstants.OCCASIONS) { occ ->
                        FilterChip(
                            selected = occasion == occ,
                            onClick = { occasion = occ },
                            label = { Text(occ) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateIso,
                        onValueChange = { dateIso = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeLabel,
                        onValueChange = { timeLabel = it },
                        label = { Text("Time (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.width(108.dp)
                    )
                }

                if (forecastAdjustment != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = forecastAdjustment.advisoryHeadline,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = forecastAdjustment.adjustmentTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val suggested = forecastAdjustment.suggestedWardrobeOutfit
                            if (suggested != null) {
                                TextButton(
                                    onClick = {
                                        selectedOutfit = null
                                        selectedWardrobeIds = suggested.items.map { it.id }.toSet()
                                        if (notes.isBlank()) {
                                            notes = forecastAdjustment.adjustmentTip
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Use Weather Look: ${suggested.items.joinToString(" + ") { it.name }}")
                                }
                            }
                        }
                    }
                }

                if (savedOutfits.isNotEmpty()) {
                    Text("Attach Saved Look", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(savedOutfits) { outfit ->
                            FilterChip(
                                selected = selectedOutfit?.id == outfit.id,
                                onClick = {
                                    selectedOutfit = if (selectedOutfit?.id == outfit.id) null else outfit
                                    selectedWardrobeIds = emptySet()
                                },
                                label = { Text(outfit.title) }
                            )
                        }
                    }
                }

                if (wardrobeItems.isNotEmpty()) {
                    Text("Or Pick Direct Wardrobe Pieces", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(wardrobeItems) { item ->
                            val picked = item.id in selectedWardrobeIds
                            FilterChip(
                                selected = picked,
                                onClick = {
                                    selectedOutfit = null
                                    selectedWardrobeIds = if (picked) selectedWardrobeIds - item.id else selectedWardrobeIds + item.id
                                },
                                label = { Text("${item.colorName} ${item.name}") }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Styling Reminder Notification", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }

                if (reminderEnabled) {
                    Text("Remind Me", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AtelierConstants.REMINDER_LEAD_OPTIONS) { (minutes, label) ->
                            FilterChip(
                                selected = reminderLeadMinutes == minutes,
                                onClick = { reminderLeadMinutes = minutes },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Prep Notes (Optional)") },
                    placeholder = { Text("e.g. Iron linen blazer, pair leather loafers") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val customSummary = wardrobeItems
                        .filter { it.id in selectedWardrobeIds }
                        .joinToString(" + ") { it.name }
                    onSchedule(
                        eventTitle.trim().ifEmpty { occasion },
                        occasion,
                        dateIso.trim(),
                        timeLabel.trim().ifEmpty { "18:00" },
                        selectedOutfit,
                        customSummary,
                        reminderLeadMinutes,
                        reminderEnabled,
                        notes.trim()
                    )
                },
                modifier = Modifier.testTag("confirm_schedule_event_button")
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
