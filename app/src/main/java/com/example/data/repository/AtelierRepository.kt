package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AtelierConstants
import com.example.data.model.DiscountAlert
import com.example.data.model.PlannedOutfitEvent
import com.example.data.model.SavedOutfit
import com.example.data.model.UserStyleProfile
import com.example.data.model.WardrobeItem
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AtelierRepository(private val db: FirebaseFirestore) {

    // CRITICAL: Always initialize Firestore with R.string.firestore_database_id (never parameterless)
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    private fun <T> Flow<T>.handleObserveErrors(
        uid: String,
        operationType: OperationType,
        path: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // --- User Style Profile ---

    fun observeUserProfile(): Flow<UserStyleProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserStyleProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .handleObserveErrors(uid, OperationType.GET, path)
        )
    }

    suspend fun ensureUserProfile(defaultName: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val safeName = defaultName.trim().take(100)
                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "gender" to "Women",
                    "stylePreferences" to listOf("Minimalist", "Classic Tailored"),
                    "budgetTier" to "Contemporary",
                    "bodyType" to "Athletic / Rectangle",
                    "heightCm" to 170,
                    "chestCm" to 92,
                    "waistCm" to 74,
                    "hipCm" to 96,
                    "shoulderCm" to 42,
                    "skinTone" to "Medium",
                    "skinUndertone" to "Warm",
                    "bestColors" to listOf("Warm Ivory", "Terracotta", "Olive Sage", "Champagne"),
                    "avoidColors" to listOf("Harsh Neon Yellow", "Icy Silver"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateStylePreferences(
        displayName: String,
        gender: String,
        stylePreferences: List<String>,
        budgetTier: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val safeGender = if (gender in AtelierConstants.GENDERS) gender else "Women"
            val safeBudget = if (budgetTier in AtelierConstants.BUDGET_TIERS) budgetTier else "Contemporary"
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val createPayload = mapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to displayName.trim().take(100),
                    "gender" to safeGender,
                    "stylePreferences" to stylePreferences.take(12),
                    "budgetTier" to safeBudget,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf<String, Any>(
                    "displayName" to displayName.trim().take(100),
                    "gender" to safeGender,
                    "stylePreferences" to stylePreferences.take(12),
                    "budgetTier" to safeBudget,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateBodyScanProfile(
        bodyType: String,
        heightCm: Int,
        chestCm: Int,
        waistCm: Int,
        hipCm: Int,
        shoulderCm: Int,
        skinTone: String,
        skinUndertone: String,
        bestColors: List<String>,
        avoidColors: List<String>,
        fitSummary: String,
        bodyAnalysisNotes: String,
        bodyScanLocalPath: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            val updateFields = mutableMapOf<String, Any>(
                "bodyType" to bodyType.trim().take(60),
                "heightCm" to heightCm.coerceIn(0, 260),
                "chestCm" to chestCm.coerceIn(0, 220),
                "waistCm" to waistCm.coerceIn(0, 220),
                "hipCm" to hipCm.coerceIn(0, 220),
                "shoulderCm" to shoulderCm.coerceIn(0, 100),
                "skinTone" to skinTone.trim().take(60),
                "skinUndertone" to skinUndertone.trim().take(60),
                "bestColors" to bestColors.map { it.trim().take(40) }.take(12),
                "avoidColors" to avoidColors.map { it.trim().take(40) }.take(12),
                "fitSummary" to fitSummary.trim().take(600),
                "bodyAnalysisNotes" to bodyAnalysisNotes.trim().take(2000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!bodyScanLocalPath.isNullOrBlank()) {
                updateFields["bodyScanLocalPath"] = bodyScanLocalPath.take(400)
            }
            if (!snapshot.exists()) {
                updateFields["userId"] = uid
                updateFields["gender"] = "Women"
                updateFields["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(updateFields).await()
            } else {
                docRef.update(updateFields).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- Virtual Wardrobe Items ---

    fun observeWardrobeItems(): Flow<List<WardrobeItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/wardrobeItems"
        emitAll(
            db.collection("users").document(uid).collection("wardrobeItems")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WardrobeItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .handleObserveErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun addWardrobeItem(item: WardrobeItem): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/wardrobeItems"
        return try {
            val uid = requireUserId()
            val safeName = item.name.trim().take(100)
            require(safeName.isNotEmpty()) { "Garment name is required." }
            val allowedCategories = AtelierConstants.GARMENT_CATEGORIES + "Footwear"
            val safeCategory = if (item.category in allowedCategories) item.category else "Top"
            val safeColor = item.colorName.trim().ifEmpty { "Neutral" }.take(60)
            val safeMaterial = if (item.materialType in AtelierConstants.MATERIAL_TYPES) {
                item.materialType
            } else {
                item.resolvedMaterial().displayName
            }
            val safeCondition = if (item.conditionStatus in AtelierConstants.CONDITION_STATUSES) {
                item.conditionStatus
            } else {
                "Good"
            }
            val safeKeep = if (item.keepDecision in AtelierConstants.KEEP_DECISIONS) {
                item.keepDecision
            } else {
                "Keep"
            }

            val docRef = db.collection("users").document(uid).collection("wardrobeItems").document()
            docPath = docRef.path
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "name" to safeName,
                "category" to safeCategory,
                "colorName" to safeColor,
                "colorHex" to item.colorHex.trim().take(16).ifEmpty { "#D8CFC2" },
                "materialType" to safeMaterial,
                "fabric" to item.fabric.trim().take(80).ifEmpty { safeMaterial },
                "season" to item.season.trim().take(40).ifEmpty { "All Season" },
                "formality" to item.formality.trim().take(40).ifEmpty { "Smart Casual" },
                "conditionStatus" to safeCondition,
                "keepDecision" to safeKeep,
                "conditionNotes" to item.conditionNotes.trim().take(400).takeIf { it.isNotEmpty() },
                "brand" to item.brand.trim().take(80).takeIf { it.isNotEmpty() },
                "localImagePath" to item.localImagePath.trim().take(400).takeIf { it.isNotEmpty() },
                "ecoScore" to item.ecoScore.coerceIn(0, 100),
                "wearCount" to item.wearCount.coerceIn(0, 10000),
                "notes" to item.notes.trim().take(400).takeIf { it.isNotEmpty() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateItemCondition(
        itemId: String,
        conditionStatus: String,
        keepDecision: String,
        conditionNotes: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/wardrobeItems/$itemId"
        return try {
            val uid = requireUserId()
            require(itemId.isNotBlank()) { "Item ID must not be blank" }
            val safeCondition = if (conditionStatus in AtelierConstants.CONDITION_STATUSES) conditionStatus else "Good"
            val safeKeep = if (keepDecision in AtelierConstants.KEEP_DECISIONS) keepDecision else "Keep"
            val docRef = db.collection("users").document(uid).collection("wardrobeItems").document(itemId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "conditionStatus" to safeCondition,
                    "keepDecision" to safeKeep,
                    "conditionNotes" to conditionNotes.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun incrementWearCount(itemId: String, currentWearCount: Int): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/wardrobeItems/$itemId"
        return try {
            val uid = requireUserId()
            require(itemId.isNotBlank()) { "Item ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("wardrobeItems").document(itemId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "wearCount" to (currentWearCount + 1).coerceIn(0, 10000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteWardrobeItem(itemId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/wardrobeItems/$itemId"
        return try {
            val uid = requireUserId()
            require(itemId.isNotBlank()) { "Item ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("wardrobeItems").document(itemId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Saved Outfits ---

    fun observeSavedOutfits(): Flow<List<SavedOutfit>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/outfits"
        emitAll(
            db.collection("users").document(uid).collection("outfits")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedOutfit::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .handleObserveErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun saveOutfit(outfit: SavedOutfit): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/outfits"
        return try {
            val uid = requireUserId()
            val safeTitle = outfit.title.trim().take(120)
            require(safeTitle.isNotEmpty()) { "Outfit title is required." }
            val safeOccasion = outfit.occasion.trim().ifEmpty { "Smart Casual" }.take(60)
            val safeSource = if (outfit.sourceType in listOf("MIX_MATCH", "AI_CURATED", "TRY_ON")) {
                outfit.sourceType
            } else {
                "MIX_MATCH"
            }

            val docRef = db.collection("users").document(uid).collection("outfits").document()
            docPath = docRef.path
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "title" to safeTitle,
                "occasion" to safeOccasion,
                "itemIds" to outfit.itemIds.take(12),
                "itemSummary" to outfit.itemSummary.trim().take(500).takeIf { it.isNotEmpty() },
                "colorHarmonyScore" to outfit.colorHarmonyScore.coerceIn(0, 100),
                "fitScore" to outfit.fitScore.coerceIn(0, 100),
                "stylingNotes" to outfit.stylingNotes.trim().take(1200).takeIf { it.isNotEmpty() },
                "tryOnImagePath" to outfit.tryOnImagePath.trim().take(400).takeIf { it.isNotEmpty() },
                "sourceType" to safeSource,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun attachTryOnImageToOutfit(outfitId: String, imagePath: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/outfits/$outfitId"
        return try {
            val uid = requireUserId()
            require(outfitId.isNotBlank()) { "Outfit ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("outfits").document(outfitId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "tryOnImagePath" to imagePath.take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedOutfit(outfitId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/outfits/$outfitId"
        return try {
            val uid = requireUserId()
            require(outfitId.isNotBlank()) { "Outfit ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("outfits").document(outfitId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Calendar Planned Events ---

    fun observePlannedEvents(): Flow<List<PlannedOutfitEvent>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/plannedEvents"
        emitAll(
            db.collection("users").document(uid).collection("plannedEvents")
                .orderBy("dateIso", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PlannedOutfitEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .handleObserveErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun addPlannedEvent(event: PlannedOutfitEvent): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/plannedEvents"
        return try {
            val uid = requireUserId()
            val safeTitle = event.eventTitle.trim().take(120)
            require(safeTitle.isNotEmpty()) { "Event title is required." }
            val safeDate = event.dateIso.trim().take(20)
            require(safeDate.length >= 8) { "Valid date is required." }

            val docRef = db.collection("users").document(uid).collection("plannedEvents").document()
            docPath = docRef.path
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "eventTitle" to safeTitle,
                "occasion" to event.occasion.trim().ifEmpty { "Smart Casual" }.take(60),
                "dateIso" to safeDate,
                "timeLabel" to event.timeLabel.trim().take(30).takeIf { it.isNotEmpty() },
                "outfitId" to event.outfitId.trim().take(128).takeIf { it.isNotEmpty() },
                "outfitTitle" to event.outfitTitle.trim().take(120).takeIf { it.isNotEmpty() },
                "reminderLeadMinutes" to event.reminderLeadMinutes.coerceIn(0, 10080),
                "reminderEnabled" to event.reminderEnabled,
                "reminderEpochMillis" to event.reminderEpochMillis.coerceIn(0L, 9999999999999L),
                "notes" to event.notes.trim().take(400).takeIf { it.isNotEmpty() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleEventReminder(
        eventId: String,
        enabled: Boolean,
        reminderEpochMillis: Long
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/plannedEvents/$eventId"
        return try {
            val uid = requireUserId()
            require(eventId.isNotBlank()) { "Event ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("plannedEvents").document(eventId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "reminderEnabled" to enabled,
                    "reminderEpochMillis" to reminderEpochMillis.coerceIn(0L, 9999999999999L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePlannedEventOutfit(
        eventId: String,
        outfitTitle: String,
        notes: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/plannedEvents/$eventId"
        return try {
            val uid = requireUserId()
            require(eventId.isNotBlank()) { "Event ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("plannedEvents").document(eventId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "outfitTitle" to outfitTitle.trim().take(120),
                    "notes" to notes.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePlannedEvent(eventId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/plannedEvents/$eventId"
        return try {
            val uid = requireUserId()
            require(eventId.isNotBlank()) { "Event ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("plannedEvents").document(eventId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Seasonal Discount Alerts ---

    fun observeDiscountAlerts(): Flow<List<DiscountAlert>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/discountAlerts"
        emitAll(
            db.collection("users").document(uid).collection("discountAlerts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DiscountAlert::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .handleObserveErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun addDiscountAlert(alert: DiscountAlert): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/discountAlerts"
        return try {
            val uid = requireUserId()
            val safeItem = alert.itemName.trim().take(120)
            val safeBrand = alert.brandName.trim().take(100)
            require(safeItem.isNotEmpty() && safeBrand.isNotEmpty()) { "Item and brand names are required." }

            val docRef = db.collection("users").document(uid).collection("discountAlerts").document()
            docPath = docRef.path
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "itemName" to safeItem,
                "brandName" to safeBrand,
                "category" to alert.category.trim().take(60).takeIf { it.isNotEmpty() },
                "targetDiscountPercent" to alert.targetDiscountPercent.coerceIn(5, 90),
                "currentDiscountPercent" to alert.currentDiscountPercent.coerceIn(0, 95),
                "ecoCertified" to alert.ecoCertified,
                "activeAlert" to alert.activeAlert,
                "seasonalTag" to alert.seasonalTag.trim().take(80).takeIf { it.isNotEmpty() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleDiscountAlert(alertId: String, active: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/discountAlerts/$alertId"
        return try {
            val uid = requireUserId()
            require(alertId.isNotBlank()) { "Alert ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("discountAlerts").document(alertId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "activeAlert" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDiscountAlert(alertId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/discountAlerts/$alertId"
        return try {
            val uid = requireUserId()
            require(alertId.isNotBlank()) { "Alert ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("discountAlerts").document(alertId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
