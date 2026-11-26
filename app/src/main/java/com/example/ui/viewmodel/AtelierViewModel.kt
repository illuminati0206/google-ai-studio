package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.AtelierAiService
import com.example.data.model.AiOutfitRecommendation
import com.example.data.model.AsyncUiState
import com.example.data.model.BodyScanAnalysisResult
import com.example.data.model.DiscoverCuratedBundle
import com.example.data.model.DiscountAlert
import com.example.data.model.GarmentAutoTagResult
import com.example.data.model.PlannedOutfitEvent
import com.example.data.model.SavedOutfit
import com.example.data.model.UserStyleProfile
import com.example.data.model.WardrobeItem
import com.example.data.repository.AtelierRepository
import com.example.data.update.GitHubReleaseCheckResult
import com.example.data.update.GitHubUpdateService
import com.example.data.update.RemoteConfigUpdateInfo
import com.example.data.update.RemoteConfigUpdateService
import com.example.data.weather.WeatherForecastSnapshot
import com.example.data.weather.WeatherService
import com.example.domain.CompatibleOutfitProposal
import com.example.domain.EventWeatherAdjustment
import com.example.domain.WardrobeCompatibilityEngine
import com.example.util.ImageStorageHelper
import com.example.util.ReminderScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AtelierViewModel"

class AtelierViewModel(
    private val appContext: Context,
    private val repository: AtelierRepository,
    private val aiService: AtelierAiService,
    private val weatherService: WeatherService = WeatherService(),
    private val updateService: GitHubUpdateService = GitHubUpdateService(),
    private val remoteConfigUpdateService: RemoteConfigUpdateService = RemoteConfigUpdateService()
) : ViewModel() {

    private fun <T> unwrapFirestoreErrors(
        flow: kotlinx.coroutines.flow.Flow<T>,
        fallbackError: String
    ): StateFlow<AsyncUiState<T>> = flow
        .map<T, AsyncUiState<T>> { AsyncUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(AsyncUiState.Error(root.localizedMessage ?: fallbackError))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AsyncUiState.Loading
        )

    val profileState: StateFlow<AsyncUiState<UserStyleProfile>> = unwrapFirestoreErrors(
        repository.observeUserProfile().map { it ?: defaultProfile() },
        "Failed to load style profile."
    )

    val wardrobeState: StateFlow<AsyncUiState<List<WardrobeItem>>> = unwrapFirestoreErrors(
        repository.observeWardrobeItems(),
        "Failed to load virtual wardrobe."
    )

    val savedOutfitsState: StateFlow<AsyncUiState<List<SavedOutfit>>> = unwrapFirestoreErrors(
        repository.observeSavedOutfits(),
        "Failed to load saved outfits."
    )

    val plannedEventsState: StateFlow<AsyncUiState<List<PlannedOutfitEvent>>> = unwrapFirestoreErrors(
        repository.observePlannedEvents(),
        "Failed to load outfit calendar."
    )

    val discountAlertsState: StateFlow<AsyncUiState<List<DiscountAlert>>> = unwrapFirestoreErrors(
        repository.observeDiscountAlerts(),
        "Failed to load seasonal alerts."
    )

    // --- Live Local Weather State & Weather-Adjusted Suggestions ---
    private val _weatherState = MutableStateFlow<AsyncUiState<WeatherForecastSnapshot>>(AsyncUiState.Idle)
    val weatherState: StateFlow<AsyncUiState<WeatherForecastSnapshot>> = _weatherState.asStateFlow()

    private val _weatherCityQuery = MutableStateFlow("New Delhi")
    val weatherCityQuery: StateFlow<String> = _weatherCityQuery.asStateFlow()

    val weatherSuggestedOutfits: StateFlow<List<CompatibleOutfitProposal>> = combine(
        wardrobeState,
        profileState,
        weatherState
    ) { wState, pState, wxState ->
        val items = (wState as? AsyncUiState.Success)?.data.orEmpty()
        val profile = (pState as? AsyncUiState.Success)?.data ?: defaultProfile()
        val weather = (wxState as? AsyncUiState.Success)?.data
        if (weather == null || items.size < 2) {
            emptyList()
        } else {
            WardrobeCompatibilityEngine.generateWeatherAdjustedOutfits(
                wardrobe = items,
                profile = profile,
                weather = weather,
                maxResults = 3
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    // --- Selected Mix & Match State ---
    private val _selectedMixMatchIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMixMatchIds: StateFlow<Set<String>> = _selectedMixMatchIds.asStateFlow()

    val liveMixMatchEvaluation: StateFlow<CompatibleOutfitProposal> = combine(
        wardrobeState,
        profileState,
        _selectedMixMatchIds,
        weatherState
    ) { wState, pState, ids, wxState ->
        val items = (wState as? AsyncUiState.Success)?.data.orEmpty().filter { it.id in ids }
        val profile = (pState as? AsyncUiState.Success)?.data ?: defaultProfile()
        val weather = (wxState as? AsyncUiState.Success)?.data
        WardrobeCompatibilityEngine.evaluateCombination(
            items = items,
            profile = profile,
            temperatureC = weather?.currentTempC,
            precipitationProbPercent = weather?.precipitationProbPercent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = WardrobeCompatibilityEngine.evaluateCombination(emptyList(), defaultProfile())
    )

    val algorithmicOutfitProposals: StateFlow<List<CompatibleOutfitProposal>> = combine(
        wardrobeState,
        profileState,
        weatherState
    ) { wState, pState, wxState ->
        val items = (wState as? AsyncUiState.Success)?.data.orEmpty()
        val profile = (pState as? AsyncUiState.Success)?.data ?: defaultProfile()
        val weather = (wxState as? AsyncUiState.Success)?.data
        WardrobeCompatibilityEngine.generateCompatibleOutfits(
            wardrobe = items,
            profile = profile,
            temperatureC = weather?.currentTempC,
            precipitationProbPercent = weather?.precipitationProbPercent,
            maxResults = 5
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    // --- AI Async States ---
    private val _garmentTagState = MutableStateFlow<AsyncUiState<GarmentAutoTagResult>>(AsyncUiState.Idle)
    val garmentTagState: StateFlow<AsyncUiState<GarmentAutoTagResult>> = _garmentTagState.asStateFlow()

    private val _conditionInspectingItemId = MutableStateFlow<String?>(null)
    val conditionInspectingItemId: StateFlow<String?> = _conditionInspectingItemId.asStateFlow()

    private val _bodyScanState = MutableStateFlow<AsyncUiState<BodyScanAnalysisResult>>(AsyncUiState.Idle)
    val bodyScanState: StateFlow<AsyncUiState<BodyScanAnalysisResult>> = _bodyScanState.asStateFlow()

    private val _occasionCurationsState = MutableStateFlow<AsyncUiState<List<AiOutfitRecommendation>>>(AsyncUiState.Idle)
    val occasionCurationsState: StateFlow<AsyncUiState<List<AiOutfitRecommendation>>> = _occasionCurationsState.asStateFlow()

    private val _selectedOccasion = MutableStateFlow("Wedding / Marriage")
    val selectedOccasion: StateFlow<String> = _selectedOccasion.asStateFlow()

    private val _discoverState = MutableStateFlow<AsyncUiState<DiscoverCuratedBundle>>(AsyncUiState.Idle)
    val discoverState: StateFlow<AsyncUiState<DiscoverCuratedBundle>> = _discoverState.asStateFlow()

    // Session caches to prevent burning daily AI quota when switching tabs or occasion chips
    private val occasionCurationsCache = mutableMapOf<String, List<AiOutfitRecommendation>>()
    private var cachedDiscoverSignature: String? = null
    private var cachedDiscoverBundle: DiscoverCuratedBundle? = null

    private val _tryOnBitmapState = MutableStateFlow<AsyncUiState<Bitmap>>(AsyncUiState.Idle)
    val tryOnBitmapState: StateFlow<AsyncUiState<Bitmap>> = _tryOnBitmapState.asStateFlow()

    private val _lastTryOnSavedPath = MutableStateFlow<String?>(null)
    val lastTryOnSavedPath: StateFlow<String?> = _lastTryOnSavedPath.asStateFlow()

    private val _tryOnAngle = MutableStateFlow("Front")
    val tryOnAngle: StateFlow<String> = _tryOnAngle.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    // --- Firebase Remote Config Version Code Check ---
    private val _remoteConfigUpdateState = MutableStateFlow<AsyncUiState<RemoteConfigUpdateInfo>>(AsyncUiState.Idle)
    val remoteConfigUpdateState: StateFlow<AsyncUiState<RemoteConfigUpdateInfo>> = _remoteConfigUpdateState.asStateFlow()

    private val _remoteConfigBannerDismissed = MutableStateFlow(false)
    val remoteConfigBannerDismissed: StateFlow<Boolean> = _remoteConfigBannerDismissed.asStateFlow()

    private val _mandatoryUpdateDismissed = MutableStateFlow(false)
    val mandatoryUpdateDismissed: StateFlow<Boolean> = _mandatoryUpdateDismissed.asStateFlow()

    // --- GitHub Releases In-App Update Checker ---
    private val _githubRepo = MutableStateFlow(updateService.loadSavedRepo(appContext))
    val githubRepo: StateFlow<String> = _githubRepo.asStateFlow()

    private val _updateCheckState = MutableStateFlow<AsyncUiState<GitHubReleaseCheckResult>>(AsyncUiState.Idle)
    val updateCheckState: StateFlow<AsyncUiState<GitHubReleaseCheckResult>> = _updateCheckState.asStateFlow()

    private val _updateBannerDismissed = MutableStateFlow(false)
    val updateBannerDismissed: StateFlow<Boolean> = _updateBannerDismissed.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultName = Firebase.auth.currentUser?.displayName ?: "Style Member"
            repository.ensureUserProfile(defaultName)
        }
        fetchWeatherForCity(_weatherCityQuery.value)
        checkRemoteConfigUpdate(forceFetch = false, silent = true)
        if (_githubRepo.value.isNotBlank()) {
            checkForGitHubUpdate(silent = true)
        }
    }

    fun dismissRemoteConfigBanner() {
        _remoteConfigBannerDismissed.value = true
    }

    fun dismissMandatoryUpdateDialog() {
        _mandatoryUpdateDismissed.value = true
    }

    fun checkRemoteConfigUpdate(forceFetch: Boolean = true, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _remoteConfigUpdateState.value = AsyncUiState.Loading
            }
            try {
                val info = remoteConfigUpdateService.checkLatestVersion(forceFetch = forceFetch)
                _remoteConfigUpdateState.value = AsyncUiState.Success(info)
                if (info.isUpdateAvailable) {
                    _remoteConfigBannerDismissed.value = false
                    if (!silent) {
                        _bannerMessage.value = "New update v${info.latestVersionName} (Code ${info.latestVersionCode}) is available."
                    }
                } else if (!silent) {
                    _bannerMessage.value = "Remote Config verified: Atelier v${info.currentVersionName} (Code ${info.currentVersionCode}) is up to date."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Remote Config check failed", e)
                if (!silent) {
                    _remoteConfigUpdateState.value = AsyncUiState.Error(
                        e.localizedMessage ?: "Could not fetch Firebase Remote Config version info."
                    )
                }
            }
        }
    }

    fun dismissUpdateBanner() {
        _updateBannerDismissed.value = true
    }

    fun saveGitHubRepoAndCheck(rawRepoInput: String) {
        val normalized = updateService.normalizeAndValidateRepo(rawRepoInput)
        if (normalized == null) {
            _updateCheckState.value = AsyncUiState.Error(
                "Invalid GitHub repository. Use owner/repo format (e.g. username/atelier-app)."
            )
            return
        }
        updateService.saveRepo(appContext, normalized)
        _githubRepo.value = normalized
        _updateBannerDismissed.value = false
        checkForGitHubUpdate(silent = false)
    }

    fun checkForGitHubUpdate(silent: Boolean = false) {
        val repo = _githubRepo.value.trim()
        if (repo.isBlank()) {
            if (!silent) {
                _updateCheckState.value = AsyncUiState.Error(
                    "Enter your GitHub repository (owner/repo) first to check for releases."
                )
            }
            return
        }
        viewModelScope.launch {
            if (!silent) {
                _updateCheckState.value = AsyncUiState.Loading
            }
            try {
                val result = updateService.checkLatestRelease(repo)
                _updateCheckState.value = AsyncUiState.Success(result)
                if (result.isUpdateAvailable) {
                    _updateBannerDismissed.value = false
                    if (!silent) {
                        _bannerMessage.value = "Update ${result.latestTag} available on GitHub."
                    }
                } else if (!silent) {
                    _bannerMessage.value = "Atelier v${result.currentVersionName} is already up to date."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "GitHub update check failed", e)
                if (!silent) {
                    _updateCheckState.value = AsyncUiState.Error(
                        e.localizedMessage ?: "Unable to reach GitHub Releases."
                    )
                }
            }
        }
    }

    fun openGitHubUpdateUrl(url: String) {
        val opened = updateService.openVerifiedGitHubUrl(appContext, url)
        if (!opened) {
            _bannerMessage.value = "Could not open GitHub release link in browser."
        }
    }

    fun clearBannerMessage() {
        _bannerMessage.value = null
    }

    fun currentProfile(): UserStyleProfile {
        return (profileState.value as? AsyncUiState.Success)?.data ?: defaultProfile()
    }

    fun currentWardrobe(): List<WardrobeItem> {
        return (wardrobeState.value as? AsyncUiState.Success)?.data.orEmpty()
    }

    private fun defaultProfile(): UserStyleProfile {
        val user = Firebase.auth.currentUser
        return UserStyleProfile(
            userId = user?.uid.orEmpty(),
            displayName = user?.displayName ?: "Style Member"
        )
    }

    // --- Style Profile Updates ---

    fun saveStylePreferences(
        displayName: String,
        gender: String,
        stylePreferences: List<String>,
        budgetTier: String
    ) {
        viewModelScope.launch {
            val result = repository.updateStylePreferences(displayName, gender, stylePreferences, budgetTier)
            _bannerMessage.value = if (result.isSuccess) {
                "Style profile updated for $gender."
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Unable to save style profile."
            }
        }
    }

    fun saveManualBodyMeasurements(
        bodyType: String,
        heightCm: Int,
        chestCm: Int,
        waistCm: Int,
        hipCm: Int,
        shoulderCm: Int,
        skinTone: String,
        skinUndertone: String
    ) {
        val existing = currentProfile()
        viewModelScope.launch {
            val res = repository.updateBodyScanProfile(
                bodyType = bodyType,
                heightCm = heightCm,
                chestCm = chestCm,
                waistCm = waistCm,
                hipCm = hipCm,
                shoulderCm = shoulderCm,
                skinTone = skinTone,
                skinUndertone = skinUndertone,
                bestColors = existing.bestColors,
                avoidColors = existing.avoidColors,
                fitSummary = existing.fitSummary,
                bodyAnalysisNotes = existing.bodyAnalysisNotes,
                bodyScanLocalPath = existing.bodyScanLocalPath.ifBlank { null }
            )
            _bannerMessage.value = if (res.isSuccess) {
                "Body measurements & skin tone saved."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update measurements."
            }
        }
    }

    // --- Garment Upload & AI Auto-Tagging ---

    fun resetGarmentTagState() {
        _garmentTagState.value = AsyncUiState.Idle
    }

    fun autoTagGarmentPhoto(
        bitmap: Bitmap?,
        brandHint: String = "",
        itemHint: String = ""
    ) {
        if (bitmap == null && brandHint.isBlank() && itemHint.isBlank()) {
            _garmentTagState.value = AsyncUiState.Error("Take a photo or enter a brand/item name to auto-fill details.")
            return
        }
        viewModelScope.launch {
            _garmentTagState.value = AsyncUiState.Loading
            try {
                val tagged = aiService.lookupAndAutoTagGarment(
                    bitmap = bitmap,
                    brandHint = brandHint,
                    itemHint = itemHint
                )
                _garmentTagState.value = AsyncUiState.Success(tagged)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Garment auto-tagging timed out", e)
                _garmentTagState.value = AsyncUiState.Error("Web & vision lookup timed out after 60s. You can retry or enter details manually.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Garment auto-tagging failed", e)
                _garmentTagState.value = AsyncUiState.Error(formatAiError(e, "Could not fetch item details."))
            }
        }
    }

    fun lookupGarmentDetailsFromWeb(
        savedImagePath: String,
        brandHint: String,
        itemHint: String
    ) {
        if (savedImagePath.isBlank() && brandHint.isBlank() && itemHint.isBlank()) {
            _garmentTagState.value = AsyncUiState.Error("Add a photo or enter a Brand name to look up material & details online.")
            return
        }
        viewModelScope.launch {
            _garmentTagState.value = AsyncUiState.Loading
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageStorageHelper.decodeScaledBitmapFromPath(savedImagePath, 768)
                }
                val tagged = aiService.lookupAndAutoTagGarment(
                    bitmap = bitmap,
                    brandHint = brandHint,
                    itemHint = itemHint
                )
                _garmentTagState.value = AsyncUiState.Success(tagged)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Web garment lookup timed out", e)
                _garmentTagState.value = AsyncUiState.Error("Online lookup timed out after 60s. Tap retry or edit manually.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Web garment lookup failed", e)
                _garmentTagState.value = AsyncUiState.Error(formatAiError(e, "Online lookup failed."))
            }
        }
    }

    fun addWardrobeItem(item: WardrobeItem) {
        viewModelScope.launch {
            val result = repository.addWardrobeItem(item)
            _bannerMessage.value = if (result.isSuccess) {
                "Added \"${item.name}\" to your virtual wardrobe."
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Could not add garment."
            }
        }
    }

    fun logItemWorn(item: WardrobeItem) {
        viewModelScope.launch {
            repository.incrementWearCount(item.id, item.wearCount)
        }
    }

    fun deleteWardrobeItem(itemId: String) {
        viewModelScope.launch {
            _selectedMixMatchIds.value = _selectedMixMatchIds.value - itemId
            val result = repository.deleteWardrobeItem(itemId)
            if (result.isFailure) {
                _bannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to remove item."
            }
        }
    }

    // --- Garment & Shoe Condition Assessment (Keep / Repair / Replace) ---

    fun updateItemCondition(
        itemId: String,
        conditionStatus: String,
        keepDecision: String,
        conditionNotes: String
    ) {
        viewModelScope.launch {
            val result = repository.updateItemCondition(itemId, conditionStatus, keepDecision, conditionNotes)
            _bannerMessage.value = if (result.isSuccess) {
                "Condition saved: $conditionStatus • $keepDecision."
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to update garment condition."
            }
        }
    }

    fun inspectItemConditionWithAi(item: WardrobeItem) {
        viewModelScope.launch {
            _conditionInspectingItemId.value = item.id
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageStorageHelper.decodeScaledBitmapFromPath(item.localImagePath, 768)
                }
                val assessment = aiService.inspectGarmentCondition(item, bitmap)
                val saveRes = repository.updateItemCondition(
                    itemId = item.id,
                    conditionStatus = assessment.conditionStatus,
                    keepDecision = assessment.keepDecision,
                    conditionNotes = assessment.conditionNotes
                )
                _bannerMessage.value = if (saveRes.isSuccess) {
                    "AI Condition Check: ${assessment.conditionStatus} — ${assessment.keepDecision}."
                } else {
                    saveRes.exceptionOrNull()?.localizedMessage ?: "Assessed condition but failed to save."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Condition check timed out", e)
                _bannerMessage.value = "Condition check timed out after 60s. Please retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Condition check failed", e)
                _bannerMessage.value = formatAiError(e, "Could not inspect item condition.")
            } finally {
                _conditionInspectingItemId.value = null
            }
        }
    }

    // --- Local Weather Fetching & Event Planner Auto-Adjustment ---

    fun fetchWeatherForCity(city: String) {
        val cleaned = city.trim().ifEmpty { "New Delhi" }
        _weatherCityQuery.value = cleaned
        viewModelScope.launch {
            _weatherState.value = AsyncUiState.Loading
            try {
                val snapshot = weatherService.fetchWeatherByCity(cleaned)
                _weatherState.value = AsyncUiState.Success(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Weather fetch for city failed", e)
                _weatherState.value = AsyncUiState.Error(e.localizedMessage ?: "Could not load weather for $cleaned.")
            }
        }
    }

    fun fetchWeatherFromDeviceLocation() {
        viewModelScope.launch {
            _weatherState.value = AsyncUiState.Loading
            try {
                val coords = withContext(Dispatchers.IO) {
                    weatherService.readLastKnownCoarseLocation(appContext)
                }
                if (coords != null) {
                    val snapshot = weatherService.fetchWeatherByCoordinates(
                        latitude = coords.first,
                        longitude = coords.second,
                        locationLabel = "Your Local Area"
                    )
                    _weatherState.value = AsyncUiState.Success(snapshot)
                    _bannerMessage.value = "Synced live local weather (${snapshot.currentTempC}°C, ${snapshot.conditionLabel})."
                } else {
                    val fallback = weatherService.fetchWeatherByCity(_weatherCityQuery.value)
                    _weatherState.value = AsyncUiState.Success(fallback)
                    _bannerMessage.value = "No cached GPS fix yet—showing live weather for ${fallback.locationName}."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Local weather fetch failed", e)
                _weatherState.value = AsyncUiState.Error(e.localizedMessage ?: "Unable to fetch local weather.")
            }
        }
    }

    fun getEventWeatherAdjustment(event: PlannedOutfitEvent): EventWeatherAdjustment? {
        val weather = (weatherState.value as? AsyncUiState.Success)?.data ?: return null
        val forecast = weather.forecastForDate(event.dateIso) ?: return null
        return WardrobeCompatibilityEngine.evaluateEventWeatherAdjustment(
            forecast = forecast,
            occasion = event.occasion,
            wardrobe = currentWardrobe(),
            profile = currentProfile()
        )
    }

    fun applyWeatherAdjustedOutfitToEvent(
        event: PlannedOutfitEvent,
        adjustment: EventWeatherAdjustment
    ) {
        val proposal = adjustment.suggestedWardrobeOutfit
        val newOutfitTitle = if (proposal != null) {
            "${proposal.title}: ${proposal.items.joinToString(" + ") { it.name }}".take(120)
        } else {
            event.outfitTitle.ifBlank { "Weather-Adjusted ${adjustment.recommendedMaterials.firstOrNull() ?: "Layered"} Look" }
        }
        val weatherPrefix = "[Weather ${adjustment.forecast.tempMinC}°–${adjustment.forecast.tempMaxC}°C, ${adjustment.forecast.precipitationProbPercent}% rain] ${adjustment.adjustmentTip}"
        val mergedNotes = weatherPrefix.take(400)

        viewModelScope.launch {
            val res = repository.updatePlannedEventOutfit(
                eventId = event.id,
                outfitTitle = newOutfitTitle,
                notes = mergedNotes
            )
            if (res.isSuccess) {
                if (event.reminderEnabled && event.reminderEpochMillis > System.currentTimeMillis()) {
                    ReminderScheduler.scheduleStylingReminder(
                        context = appContext,
                        eventId = event.id,
                        eventTitle = event.eventTitle,
                        outfitTitle = newOutfitTitle,
                        triggerAtMillis = event.reminderEpochMillis
                    )
                }
                _bannerMessage.value = "Updated \"${event.eventTitle}\" with weather-matched look."
            } else {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Could not update event outfit."
            }
        }
    }

    // --- Mix & Match Outfit Composer ---

    fun toggleMixMatchItem(itemId: String) {
        val current = _selectedMixMatchIds.value
        _selectedMixMatchIds.value = if (itemId in current) current - itemId else current + itemId
    }

    fun selectMixMatchSet(itemIds: List<String>) {
        _selectedMixMatchIds.value = itemIds.toSet()
    }

    fun clearMixMatchSelection() {
        _selectedMixMatchIds.value = emptySet()
    }

    fun saveCurrentMixMatchOutfit(title: String, occasion: String) {
        val selected = currentWardrobe().filter { it.id in _selectedMixMatchIds.value }
        if (selected.isEmpty()) {
            _bannerMessage.value = "Select at least one wardrobe piece first."
            return
        }
        val eval = liveMixMatchEvaluation.value
        viewModelScope.launch {
            val outfit = SavedOutfit(
                title = title.ifBlank { eval.title },
                occasion = occasion,
                itemIds = selected.map { it.id },
                itemSummary = selected.joinToString(" + ") { "${it.colorName} ${it.name}" },
                colorHarmonyScore = eval.colorHarmonyScore,
                fitScore = eval.fitAndUndertoneScore,
                stylingNotes = eval.rationale,
                sourceType = "MIX_MATCH"
            )
            val result = repository.saveOutfit(outfit)
            _bannerMessage.value = if (result.isSuccess) {
                "Saved \"${outfit.title}\" to your Lookbook."
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to save outfit."
            }
        }
    }

    fun saveAiOutfitToLookbook(rec: AiOutfitRecommendation) {
        viewModelScope.launch {
            val outfit = SavedOutfit(
                title = rec.title,
                occasion = rec.occasion,
                itemIds = emptyList(),
                itemSummary = rec.itemsUsed.joinToString(" • "),
                colorHarmonyScore = rec.harmonyScore,
                fitScore = rec.harmonyScore,
                stylingNotes = "${rec.trendTag}: ${rec.colorTheoryNote} ${rec.bodyFitAdvice}",
                sourceType = "AI_CURATED"
            )
            val res = repository.saveOutfit(outfit)
            _bannerMessage.value = if (res.isSuccess) {
                "Saved \"${rec.title}\" to your Lookbook."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to save curated look."
            }
        }
    }

    fun deleteSavedOutfit(outfitId: String) {
        viewModelScope.launch {
            repository.deleteSavedOutfit(outfitId)
        }
    }

    // --- Full Body Structure Scan & Skin Tone Match (Gemini Multimodal) ---

    fun runFullBodyStructureScan(bodyPhoto: Bitmap?, savedLocalPath: String?) {
        val profile = currentProfile()
        viewModelScope.launch {
            _bodyScanState.value = AsyncUiState.Loading
            try {
                val result = aiService.analyzeBodyStructureAndSkinTone(bodyPhoto, profile)
                _bodyScanState.value = AsyncUiState.Success(result)
                repository.updateBodyScanProfile(
                    bodyType = result.bodyType,
                    heightCm = result.estimatedHeightCm,
                    chestCm = profile.chestCm,
                    waistCm = profile.waistCm,
                    hipCm = profile.hipCm,
                    shoulderCm = profile.shoulderCm,
                    skinTone = result.skinTone,
                    skinUndertone = result.skinUndertone,
                    bestColors = result.bestColors,
                    avoidColors = result.avoidColors,
                    fitSummary = result.fitSummary,
                    bodyAnalysisNotes = result.detailedNotes,
                    bodyScanLocalPath = savedLocalPath ?: profile.bodyScanLocalPath.ifBlank { null }
                )
                _bannerMessage.value = "Full body structure & skin tone profile synced."
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Body scan analysis timed out", e)
                _bodyScanState.value = AsyncUiState.Error("Body scan timed out after 60s. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Body scan analysis failed", e)
                _bodyScanState.value = AsyncUiState.Error(formatAiError(e, "Body structure analysis failed."))
            }
        }
    }

    // --- Occasion & Trending AI Outfit Curation ---

    fun selectOccasionAndCurate(occasion: String, forceRefresh: Boolean = false) {
        _selectedOccasion.value = occasion
        val profile = currentProfile()
        val wardrobe = currentWardrobe()
        val cacheKey = "${profile.gender}|${profile.bodyType}|${wardrobe.size}|$occasion"
        if (!forceRefresh) {
            occasionCurationsCache[cacheKey]?.let { cached ->
                _occasionCurationsState.value = AsyncUiState.Success(cached)
                return
            }
        }
        viewModelScope.launch {
            _occasionCurationsState.value = AsyncUiState.Loading
            try {
                val curations = aiService.curateOccasionOutfits(profile, wardrobe, occasion)
                occasionCurationsCache[cacheKey] = curations
                _occasionCurationsState.value = AsyncUiState.Success(curations)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Occasion curation timed out", e)
                _occasionCurationsState.value = AsyncUiState.Error("Styling request timed out. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Occasion curation failed", e)
                _occasionCurationsState.value = AsyncUiState.Error(formatAiError(e, "Could not generate occasion looks."))
            }
        }
    }

    // --- Discover: What to Buy (Upgrades for Old Clothes) + Sustainable Brands ---

    fun loadStyleUpgradesAndEcoBrands(
        focusOccasion: String = _selectedOccasion.value,
        forceRefresh: Boolean = false
    ) {
        val profile = currentProfile()
        val wardrobe = currentWardrobe()
        val signature = "${profile.gender}|${profile.budgetTier}|${wardrobe.size}|$focusOccasion"
        if (!forceRefresh && cachedDiscoverSignature == signature && cachedDiscoverBundle != null) {
            _discoverState.value = AsyncUiState.Success(cachedDiscoverBundle!!)
            return
        }
        viewModelScope.launch {
            _discoverState.value = AsyncUiState.Loading
            try {
                val bundle = aiService.generateStyleUpgradesAndEcoBrands(profile, wardrobe, focusOccasion)
                cachedDiscoverSignature = signature
                cachedDiscoverBundle = bundle
                _discoverState.value = AsyncUiState.Success(bundle)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Discover generation timed out", e)
                _discoverState.value = AsyncUiState.Error("Upgrade discovery timed out. Please tap Retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Discover generation failed", e)
                _discoverState.value = AsyncUiState.Error(formatAiError(e, "Failed to fetch sustainable upgrades."))
            }
        }
    }

    private fun formatAiError(error: Throwable, fallback: String): String {
        val raw = error.localizedMessage.orEmpty()
        val lower = raw.lowercase()
        return when {
            lower.contains("quota") || lower.contains("rate-limit") || lower.contains("limit exceeded") ->
                "Daily AI Free Tier limit reached on all available Gemini models. Wait for the quota reset or upgrade the Firebase project plan to Blaze for higher limits."
            raw.isNotBlank() -> raw
            else -> fallback
        }
    }

    // --- High-Fidelity 3D Virtual Try-On Renderer (gemini-nano-banana-2.1) ---

    fun setTryOnAngle(angle: String) {
        _tryOnAngle.value = angle
    }

    fun render3dVirtualTryOn(
        customOutfitPrompt: String,
        occasion: String,
        angle: String = _tryOnAngle.value
    ) {
        _tryOnAngle.value = angle
        val profile = currentProfile()
        val selectedItems = currentWardrobe().filter { it.id in _selectedMixMatchIds.value }

        viewModelScope.launch {
            _tryOnBitmapState.value = AsyncUiState.Loading
            try {
                val (bodyBitmap, garmentBitmaps) = withContext(Dispatchers.IO) {
                    val bodyBmp = ImageStorageHelper.decodeScaledBitmapFromPath(profile.bodyScanLocalPath, 768)
                    val gBmps = selectedItems.mapNotNull { item ->
                        ImageStorageHelper.decodeScaledBitmapFromPath(item.localImagePath, 768)
                    }
                    bodyBmp to gBmps
                }

                val renderedBitmap = aiService.generate3dVirtualTryOn(
                    profile = profile,
                    selectedItems = selectedItems,
                    customOutfitDescription = customOutfitPrompt,
                    occasion = occasion,
                    angle = angle,
                    bodyReferenceBitmap = bodyBitmap,
                    garmentBitmaps = garmentBitmaps
                )

                val savedPath = withContext(Dispatchers.IO) {
                    ImageStorageHelper.saveBitmap(appContext, renderedBitmap, "tryon", "studio_3d")
                }
                _lastTryOnSavedPath.value = savedPath
                _tryOnBitmapState.value = AsyncUiState.Success(renderedBitmap)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "3D Try-On render timed out", e)
                _tryOnBitmapState.value = AsyncUiState.Error("3D Studio rendering timed out after 60 seconds. Tap retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "3D Try-On render failed", e)
                _tryOnBitmapState.value = AsyncUiState.Error(formatAiError(e, "3D virtual try-on generation failed."))
            }
        }
    }

    fun saveRenderedTryOnAsOutfit(title: String, occasion: String) {
        val selected = currentWardrobe().filter { it.id in _selectedMixMatchIds.value }
        val savedPath = _lastTryOnSavedPath.value.orEmpty()
        viewModelScope.launch {
            val outfit = SavedOutfit(
                title = title.ifBlank { "3D Studio Look • $occasion" },
                occasion = occasion,
                itemIds = selected.map { it.id },
                itemSummary = selected.joinToString(" + ") { it.name }.ifBlank { "3D Bespoke Studio Look" },
                colorHarmonyScore = if (selected.isNotEmpty()) liveMixMatchEvaluation.value.colorHarmonyScore else 93,
                fitScore = 95,
                stylingNotes = "Rendered in Atelier 3D Studio (${_tryOnAngle.value} view) for ${currentProfile().bodyType}.",
                tryOnImagePath = savedPath,
                sourceType = "TRY_ON"
            )
            val res = repository.saveOutfit(outfit)
            _bannerMessage.value = if (res.isSuccess) {
                "3D Try-On Look saved to Lookbook."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Could not save 3D look."
            }
        }
    }

    // --- Seasonal Discount Alerts Watchlist ---

    fun addDiscountAlert(
        itemName: String,
        brandName: String,
        category: String,
        targetDiscountPercent: Int,
        currentDiscountPercent: Int,
        ecoCertified: Boolean,
        seasonalTag: String
    ) {
        viewModelScope.launch {
            val alert = DiscountAlert(
                itemName = itemName,
                brandName = brandName,
                category = category,
                targetDiscountPercent = targetDiscountPercent,
                currentDiscountPercent = currentDiscountPercent,
                ecoCertified = ecoCertified,
                activeAlert = true,
                seasonalTag = seasonalTag
            )
            val res = repository.addDiscountAlert(alert)
            _bannerMessage.value = if (res.isSuccess) {
                "Seasonal discount alert set for $itemName ($targetDiscountPercent%+)."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to create alert."
            }
        }
    }

    fun toggleDiscountAlert(alert: DiscountAlert, active: Boolean) {
        viewModelScope.launch {
            repository.toggleDiscountAlert(alert.id, active)
        }
    }

    fun deleteDiscountAlert(alertId: String) {
        viewModelScope.launch {
            repository.deleteDiscountAlert(alertId)
        }
    }

    // --- Outfit Planner Calendar & Reminder Notifications ---

    fun schedulePlannedOutfitEvent(
        eventTitle: String,
        occasion: String,
        dateIso: String,
        timeLabel: String,
        outfit: SavedOutfit?,
        reminderLeadMinutes: Int,
        reminderEnabled: Boolean,
        notes: String
    ) {
        val triggerMillis = if (reminderEnabled) {
            ReminderScheduler.computeReminderEpochMillis(dateIso, timeLabel, reminderLeadMinutes)
        } else {
            0L
        }

        viewModelScope.launch {
            val event = PlannedOutfitEvent(
                eventTitle = eventTitle,
                occasion = occasion,
                dateIso = dateIso,
                timeLabel = timeLabel,
                outfitId = outfit?.id.orEmpty(),
                outfitTitle = outfit?.title.orEmpty(),
                reminderLeadMinutes = reminderLeadMinutes,
                reminderEnabled = reminderEnabled,
                reminderEpochMillis = triggerMillis,
                notes = notes
            )
            val result = repository.addPlannedEvent(event)
            if (result.isSuccess) {
                val createdId = result.getOrNull().orEmpty()
                if (reminderEnabled && createdId.isNotBlank()) {
                    ReminderScheduler.scheduleStylingReminder(
                        context = appContext,
                        eventId = createdId,
                        eventTitle = eventTitle,
                        outfitTitle = outfit?.title.orEmpty(),
                        triggerAtMillis = triggerMillis
                    )
                }
                _bannerMessage.value = if (reminderEnabled) {
                    "Planned \"$eventTitle\" on $dateIso with reminder alert."
                } else {
                    "Planned \"$eventTitle\" on $dateIso."
                }
            } else {
                _bannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Could not save planned event."
            }
        }
    }

    fun toggleEventReminder(event: PlannedOutfitEvent, enabled: Boolean) {
        val triggerMillis = if (enabled) {
            ReminderScheduler.computeReminderEpochMillis(event.dateIso, event.timeLabel, event.reminderLeadMinutes)
        } else {
            0L
        }
        viewModelScope.launch {
            val res = repository.toggleEventReminder(event.id, enabled, triggerMillis)
            if (res.isSuccess) {
                if (enabled) {
                    ReminderScheduler.scheduleStylingReminder(
                        context = appContext,
                        eventId = event.id,
                        eventTitle = event.eventTitle,
                        outfitTitle = event.outfitTitle,
                        triggerAtMillis = triggerMillis
                    )
                } else {
                    ReminderScheduler.cancelStylingReminder(appContext, event.id)
                }
            }
        }
    }

    fun deletePlannedEvent(eventId: String) {
        viewModelScope.launch {
            ReminderScheduler.cancelStylingReminder(appContext, eventId)
            repository.deletePlannedEvent(eventId)
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appCtx = context.applicationContext
                AtelierViewModel(
                    appContext = appCtx,
                    repository = AtelierRepository(appCtx),
                    aiService = AtelierAiService()
                )
            }
        }
    }
}
