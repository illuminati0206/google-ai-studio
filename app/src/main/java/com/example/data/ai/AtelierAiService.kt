package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiOutfitRecommendation
import com.example.data.model.AtelierConstants
import com.example.data.model.BodyScanAnalysisResult
import com.example.data.model.ConditionInspectionResult
import com.example.data.model.DiscoverCuratedBundle
import com.example.data.model.GarmentAutoTagResult
import com.example.data.model.StyleUpgradeSuggestion
import com.example.data.model.SustainableBrandMatch
import com.example.data.model.UserStyleProfile
import com.example.data.model.WardrobeItem
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "AtelierAiService"
private const val PRIMARY_TEXT_MODEL = "gemini-3.8-flash"
private const val FALLBACK_TEXT_MODEL = "gemini-3.5-flash-lite"
private const val PRIMARY_IMAGE_MODEL = "gemini-nano-banana-2.1"
private const val FALLBACK_IMAGE_MODEL = "gemini-3.1-flash-lite-image"

class AtelierAiService {

    // --- Shared Structured Schemas ---

    private val garmentTagConfig: GenerationConfig by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "name" to Schema.string(description = "Concise editorial or official catalog name of the garment or shoe, e.g. Zara Relaxed Linen Shirt or Nike Air Force 1 '07"),
                "brand" to Schema.string(description = "Verified brand name if visible on label/logo or provided by user, else empty string"),
                "category" to Schema.enumeration(
                    values = AtelierConstants.GARMENT_CATEGORIES,
                    description = "Exact wardrobe category (use Shoes for footwear)"
                ),
                "colorName" to Schema.string(description = "Descriptive fashion color name, e.g. Warm Ivory, Terracotta Rust"),
                "colorHex" to Schema.string(description = "Dominant hex color code like #C87D55"),
                "materialType" to Schema.enumeration(
                    values = AtelierConstants.MATERIAL_TYPES,
                    description = "Primary material category (Silk, Cotton, Wool, Linen, Cashmere, Denim, Leather, Velvet, Synthetic Blend)"
                ),
                "fabric" to Schema.string(description = "Exact fabric/material composition from official brand specs or visual weave, e.g. 100% European Flax Linen, Full-Grain Leather Upper"),
                "season" to Schema.enumeration(
                    values = AtelierConstants.SEASONS,
                    description = "Best suited season"
                ),
                "formality" to Schema.enumeration(
                    values = AtelierConstants.FORMALITIES,
                    description = "Primary formality tier"
                ),
                "conditionStatus" to Schema.enumeration(
                    values = AtelierConstants.CONDITION_STATUSES,
                    description = "Visual wear & condition quality: Mint, Good, Fair / Needs Care, or Worn / Damaged"
                ),
                "keepDecision" to Schema.enumeration(
                    values = AtelierConstants.KEEP_DECISIONS,
                    description = "Actionable wardrobe decision: Keep, Repair / Dry Clean, or Donate / Replace"
                ),
                "conditionNotes" to Schema.string(description = "Short honest assessment of fabric/sole condition, pilling, fading, or care advice"),
                "ecoScore" to Schema.integer(description = "Estimated sustainability score 40 to 98 based on natural/durable fiber and brand practices"),
                "stylingNote" to Schema.string(description = "One-sentence bespoke styling tip for this piece"),
                "webSourceSummary" to Schema.string(description = "Short note of web/brand verification if matched online, e.g. Verified via Zara catalog specs")
            )
        )
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
        }
    }

    private val conditionInspectorConfig: GenerationConfig by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "conditionStatus" to Schema.enumeration(
                    values = AtelierConstants.CONDITION_STATUSES,
                    description = "Condition assessment of garment or shoes"
                ),
                "keepDecision" to Schema.enumeration(
                    values = AtelierConstants.KEEP_DECISIONS,
                    description = "Whether the user should Keep, Repair / Dry Clean, or Donate / Replace"
                ),
                "conditionNotes" to Schema.string(description = "Practical 1-2 sentence explanation of why to keep, repair, or retire this piece")
            )
        )
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
        }
    }

    private val bodyScanConfig: GenerationConfig by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "bodyType" to Schema.enumeration(
                    values = AtelierConstants.BODY_TYPES,
                    description = "Detected body structure archetype"
                ),
                "skinTone" to Schema.enumeration(
                    values = AtelierConstants.SKIN_TONES,
                    description = "Detected skin tone depth"
                ),
                "skinUndertone" to Schema.enumeration(
                    values = AtelierConstants.SKIN_UNDERTONES,
                    description = "Detected skin undertone"
                ),
                "estimatedHeightCm" to Schema.integer(description = "Height in cm"),
                "fitSummary" to Schema.string(description = "Two-sentence luxury tailor summary of garment fit & silhouette balance"),
                "detailedNotes" to Schema.string(description = "Actionable tailoring advice for shoulders, waist, hem lengths, and drape"),
                "bestColors" to Schema.array(
                    items = Schema.string(),
                    description = "5 flattering editorial color names for this skin tone"
                ),
                "avoidColors" to Schema.array(
                    items = Schema.string(),
                    description = "3 color shades to avoid"
                ),
                "flatteringCuts" to Schema.array(
                    items = Schema.string(),
                    description = "4 specific garment silhouettes/cuts that elevate this body structure"
                )
            )
        )
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
        }
    }

    private val outfitStylistConfig: GenerationConfig by lazy {
        val outfitSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Editorial outfit title"),
                "occasion" to Schema.string(description = "Occasion category"),
                "itemsUsed" to Schema.array(
                    items = Schema.string(),
                    description = "List of garments making up this look, prioritizing user's existing wardrobe"
                ),
                "trendTag" to Schema.string(description = "2026 fashion trend alignment tag"),
                "colorTheoryNote" to Schema.string(description = "Color harmony explanation"),
                "bodyFitAdvice" to Schema.string(description = "Precise fit & proportion tailoring tip for the user's body structure"),
                "skinToneNote" to Schema.string(description = "Why these shades flatter the user's skin tone & undertone"),
                "harmonyScore" to Schema.integer(description = "Overall style harmony score between 82 and 99")
            )
        )
        val wrapperSchema = Schema.obj(
            properties = mapOf(
                "outfits" to Schema.array(
                    items = outfitSchema,
                    description = "3 curated outfits for the requested occasion"
                )
            )
        )
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = wrapperSchema
        }
    }

    private val discoverConfig: GenerationConfig by lazy {
        val upgradeSchema = Schema.obj(
            properties = mapOf(
                "itemName" to Schema.string(description = "Recommended new garment to buy"),
                "category" to Schema.enumeration(values = AtelierConstants.GARMENT_CATEGORIES),
                "colorName" to Schema.string(description = "Recommended shade matching user's skin tone"),
                "pairsWithExisting" to Schema.array(
                    items = Schema.string(),
                    description = "Names of existing wardrobe pieces this new purchase elevates"
                ),
                "whyItUpgrades" to Schema.string(description = "How this upgrades the user's personal style"),
                "sustainableBrand" to Schema.string(description = "Real ethical or sustainable fashion brand"),
                "ecoCertification" to Schema.string(description = "e.g. GOTS Organic, B-Corp, Fair Trade, OEKO-TEX"),
                "estimatedPriceRange" to Schema.string(description = "Aligned to user's budget tier"),
                "seasonalDealHint" to Schema.string(description = "Current seasonal discount or archive sale opportunity")
            )
        )
        val brandSchema = Schema.obj(
            properties = mapOf(
                "brandName" to Schema.string(),
                "specialty" to Schema.string(),
                "ecoCertification" to Schema.string(),
                "seasonalOffer" to Schema.string(),
                "discountPercent" to Schema.integer(description = "Typical seasonal discount percentage 10 to 50"),
                "whyItFitsUser" to Schema.string()
            )
        )
        val bundleSchema = Schema.obj(
            properties = mapOf(
                "trendHeadline" to Schema.string(description = "Concise 2026 fashion trend headline tailored to user"),
                "upgrades" to Schema.array(items = upgradeSchema, description = "3 high-impact wardrobe upgrade items"),
                "sustainableBrands" to Schema.array(items = brandSchema, description = "3 sustainable fashion brands")
            )
        )
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = bundleSchema
        }
    }

    private val tryOnImageConfig: GenerationConfig by lazy {
        generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
        }
    }

    // --- Lazily Initialized Primary & High-Volume Fallback Models ---

    private val webSearchPrimaryModel by lazy {
        Firebase.ai.generativeModel(modelName = PRIMARY_TEXT_MODEL, tools = listOf(Tool.googleSearch()))
    }
    private val webSearchFallbackModel by lazy {
        Firebase.ai.generativeModel(modelName = FALLBACK_TEXT_MODEL, tools = listOf(Tool.googleSearch()))
    }

    private val garmentTagPrimaryModel by lazy {
        Firebase.ai.generativeModel(modelName = PRIMARY_TEXT_MODEL, generationConfig = garmentTagConfig)
    }
    private val garmentTagFallbackModel by lazy {
        Firebase.ai.generativeModel(modelName = FALLBACK_TEXT_MODEL, generationConfig = garmentTagConfig)
    }

    private val conditionPrimaryModel by lazy {
        Firebase.ai.generativeModel(modelName = PRIMARY_TEXT_MODEL, generationConfig = conditionInspectorConfig)
    }
    private val conditionFallbackModel by lazy {
        Firebase.ai.generativeModel(modelName = FALLBACK_TEXT_MODEL, generationConfig = conditionInspectorConfig)
    }

    private val bodyScanPrimaryModel by lazy {
        Firebase.ai.generativeModel(modelName = PRIMARY_TEXT_MODEL, generationConfig = bodyScanConfig)
    }
    private val bodyScanFallbackModel by lazy {
        Firebase.ai.generativeModel(modelName = FALLBACK_TEXT_MODEL, generationConfig = bodyScanConfig)
    }

    private val outfitStylistPrimaryModel by lazy {
        Firebase.ai.generativeModel(modelName = PRIMARY_TEXT_MODEL, generationConfig = outfitStylistConfig)
    }
    private val outfitStylistFallbackModel by lazy {
        Firebase.ai.generativeModel(modelName = FALLBACK_TEXT_MODEL, generationConfig = outfitStylistConfig)
    }

    private val discoverPrimaryModel by lazy {
        Firebase.ai.generativeModel(modelName = PRIMARY_TEXT_MODEL, generationConfig = discoverConfig)
    }
    private val discoverFallbackModel by lazy {
        Firebase.ai.generativeModel(modelName = FALLBACK_TEXT_MODEL, generationConfig = discoverConfig)
    }

    private val tryOnPrimaryModel by lazy {
        Firebase.ai.generativeModel(modelName = PRIMARY_IMAGE_MODEL, generationConfig = tryOnImageConfig)
    }
    private val tryOnFallbackModel by lazy {
        Firebase.ai.generativeModel(modelName = FALLBACK_IMAGE_MODEL, generationConfig = tryOnImageConfig)
    }

    // --- Automatic Quota & Demand-Spike Resilience Helper ---

    private val modelCooldownUntilMs = ConcurrentHashMap<String, Long>()

    private fun isQuotaOrRateLimitError(error: Throwable): Boolean {
        val chain = generateSequence(error) { it.cause }.toList()
        val hasQuotaClass = chain.any { it::class.java.simpleName.lowercase().contains("quota") }
        val msg = chain.mapNotNull { it.message }.joinToString(" ").lowercase()
        return hasQuotaClass ||
            msg.contains("quota") ||
            msg.contains("rate-limit") ||
            msg.contains("rate limit") ||
            msg.contains("limit exceeded") ||
            msg.contains("resource_exhausted") ||
            msg.contains("429")
    }

    private fun isHighDemandOrTransientError(error: Throwable): Boolean {
        val msg = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return msg.contains("high demand") ||
            msg.contains("503") ||
            msg.contains("unavailable") ||
            msg.contains("overloaded") ||
            msg.contains("temporarily") ||
            msg.contains("try again later")
    }

    private fun isFailoverEligibleError(error: Throwable): Boolean {
        return isQuotaOrRateLimitError(error) || isHighDemandOrTransientError(error)
    }

    private fun markModelQuotaCooldown(modelName: String, error: Throwable) {
        if (!isQuotaOrRateLimitError(error)) return
        val msg = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        // Daily free-tier limit -> skip this model for 60 minutes in memory; per-minute limit -> 60s
        val cooldownMs = if (msg.contains("per day") || msg.contains("free_tier") || msg.contains("please retry in")) {
            60 * 60 * 1000L
        } else {
            60 * 1000L
        }
        modelCooldownUntilMs[modelName] = System.currentTimeMillis() + cooldownMs
        Log.w(TAG, "Marked $modelName on quota cooldown for ${cooldownMs / 1000}s; routing traffic to alternate model.")
    }

    private fun isModelInCooldown(modelName: String): Boolean {
        val until = modelCooldownUntilMs[modelName] ?: return false
        if (System.currentTimeMillis() >= until) {
            modelCooldownUntilMs.remove(modelName)
            return false
        }
        return true
    }

    private suspend fun <T> runWithDemandFallback(
        operationName: String,
        primaryModelName: String,
        primaryModel: GenerativeModel,
        fallbackModelName: String,
        fallbackModel: GenerativeModel,
        primaryTimeoutMs: Long = 16_000L,
        fallbackTimeoutMs: Long = 35_000L,
        block: suspend (GenerativeModel) -> T
    ): T {
        val skipPrimaryForQuota = isModelInCooldown(primaryModelName) && !isModelInCooldown(fallbackModelName)
        if (skipPrimaryForQuota) {
            Log.i(TAG, "$operationName bypassing $primaryModelName (quota cooldown active) -> using $fallbackModelName directly.")
            return try {
                withTimeout(fallbackTimeoutMs) {
                    block(fallbackModel)
                }
            } catch (e: TimeoutCancellationException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markModelQuotaCooldown(fallbackModelName, e)
                throw e
            }
        }

        return try {
            withTimeout(primaryTimeoutMs) {
                block(primaryModel)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "$operationName timed out on $primaryModelName after ${primaryTimeoutMs}ms; switching to $fallbackModelName.", e)
            withTimeout(fallbackTimeoutMs) {
                block(fallbackModel)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isFailoverEligibleError(e)) throw e
            markModelQuotaCooldown(primaryModelName, e)
            Log.w(TAG, "$operationName hit quota/demand limit on $primaryModelName; switching immediately to $fallbackModelName.", e)
            delay(350L)
            try {
                withTimeout(fallbackTimeoutMs) {
                    block(fallbackModel)
                }
            } catch (fallbackErr: TimeoutCancellationException) {
                throw fallbackErr
            } catch (fallbackErr: CancellationException) {
                throw fallbackErr
            } catch (fallbackErr: Exception) {
                markModelQuotaCooldown(fallbackModelName, fallbackErr)
                if (!isHighDemandOrTransientError(fallbackErr)) throw fallbackErr
                Log.w(TAG, "$operationName fallback $fallbackModelName temporarily busy; retrying once after brief backoff.", fallbackErr)
                delay(1200L)
                withTimeout(fallbackTimeoutMs) {
                    block(fallbackModel)
                }
            }
        }
    }

    // --- Public AI Operations ---

    suspend fun autoTagGarment(bitmap: Bitmap): GarmentAutoTagResult =
        lookupAndAutoTagGarment(bitmap = bitmap, brandHint = "", itemHint = "")

    /**
     * Checks live internet product catalogs (via Gemini Google Search grounding) using the user's
     * Brand name, basic item hint, and/or captured garment/shoe photo to auto-fill exact material
     * composition, category, color, season, formality, eco score, and visual condition.
     */
    suspend fun lookupAndAutoTagGarment(
        bitmap: Bitmap?,
        brandHint: String = "",
        itemHint: String = ""
    ): GarmentAutoTagResult {
        val cleanBrand = brandHint.trim()
        val cleanItem = itemHint.trim()

        // Step 1: Live Web Search Grounding (verify brand product specs & exact fabric composition online)
        var webResearchNotes = ""
        var discoveredSources = ""
        if (cleanBrand.isNotBlank() || cleanItem.isNotBlank() || bitmap != null) {
            try {
                val searchPrompt = buildString {
                    appendLine("Search the web for official product specifications and exact fabric/material composition of this fashion item or shoe.")
                    if (cleanBrand.isNotBlank()) appendLine("- Brand provided by user: $cleanBrand")
                    if (cleanItem.isNotBlank()) appendLine("- Basic item name/description provided by user: $cleanItem")
                    if (bitmap != null) appendLine("- A photo of the item is attached. Identify any visible brand logo, silhouette, exact model name, colorway, and material.")
                    appendLine("Return concise factual bullet points covering: Official Product Name, Brand, Exact Material/Fabric Composition (e.g. 100% Cotton / Full-Grain Leather / Mulberry Silk), Best Season, Formality, Sustainability/Eco notes, and Care tips.")
                }

                val webResponse = runWithDemandFallback(
                    operationName = "webSearchGarment",
                    primaryModelName = FALLBACK_TEXT_MODEL,
                    primaryModel = webSearchFallbackModel,
                    fallbackModelName = PRIMARY_TEXT_MODEL,
                    fallbackModel = webSearchPrimaryModel,
                    primaryTimeoutMs = 12_000L,
                    fallbackTimeoutMs = 16_000L
                ) { model ->
                    if (bitmap != null) {
                        model.generateContent(
                            content {
                                image(bitmap)
                                text(searchPrompt)
                            }
                        )
                    } else {
                        model.generateContent(searchPrompt)
                    }
                }
                webResearchNotes = webResponse.text.orEmpty().trim()
                val sourceTitles = webResponse.candidates
                    .firstOrNull()
                    ?.groundingMetadata
                    ?.groundingChunks
                    ?.mapNotNull { it.web?.title ?: it.web?.domain }
                    ?.distinct()
                    ?.take(3)
                    .orEmpty()
                if (sourceTitles.isNotEmpty()) {
                    discoveredSources = "Verified online: ${sourceTitles.joinToString(", ")}"
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Web grounding step timed out; continuing with structured vision/brand extraction.", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Web grounding step skipped; continuing with structured vision/brand extraction.", e)
            }
        }

        // Step 2: Structured JSON Extraction (with automatic fallback if primary model stalls or has high demand)
        val structuredPrompt = buildString {
            appendLine("You are a master luxury fashion archivist and garment care specialist.")
            appendLine("Synthesize the attached photo (if present), user's basic inputs, and the live web lookup findings below into complete structured wardrobe metadata.")
            if (cleanBrand.isNotBlank()) appendLine("User Brand Input: $cleanBrand")
            if (cleanItem.isNotBlank()) appendLine("User Basic Item Input: $cleanItem")
            if (webResearchNotes.isNotBlank()) {
                appendLine("Live Web Product Research:")
                appendLine(webResearchNotes.take(2000))
            }
            appendLine("Extract: concise product name, verified brand, category (use Shoes for footwear), fashion colorName, dominant colorHex, exact materialType (Silk, Cotton, Wool, Linen, Cashmere, Denim, Leather, Velvet, or Synthetic Blend), exact fabric composition, season, formality tier, visual conditionStatus (Mint, Good, Fair / Needs Care, Worn / Damaged), keepDecision (Keep, Repair / Dry Clean, Donate / Replace), conditionNotes, ecoScore (40-98), stylingNote, and a short webSourceSummary.")
        }

        val response = runWithDemandFallback(
            operationName = "garmentAutoTag",
            primaryModelName = PRIMARY_TEXT_MODEL,
            primaryModel = garmentTagPrimaryModel,
            fallbackModelName = FALLBACK_TEXT_MODEL,
            fallbackModel = garmentTagFallbackModel,
            primaryTimeoutMs = 15_000L,
            fallbackTimeoutMs = 30_000L
        ) { model ->
            if (bitmap != null) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(structuredPrompt)
                    }
                )
            } else {
                model.generateContent(structuredPrompt)
            }
        }

        val raw = response.text ?: throw IllegalStateException("AI returned an empty garment analysis.")
        val json = JSONObject(raw)
        val materialType = json.optString("materialType", "Cotton")
        val resolvedBrand = json.optString("brand", cleanBrand).ifBlank { cleanBrand }
        val resolvedWebSource = discoveredSources.ifBlank {
            json.optString("webSourceSummary", if (webResearchNotes.isNotBlank()) "Checked online brand catalog" else "")
        }

        return GarmentAutoTagResult(
            name = json.optString("name", cleanItem.ifBlank { "Tailored Garment" }),
            brand = resolvedBrand,
            category = json.optString("category", "Top"),
            colorName = json.optString("colorName", "Warm Neutral"),
            colorHex = json.optString("colorHex", "#D8CFC2"),
            materialType = materialType,
            fabric = json.optString("fabric", materialType),
            season = json.optString("season", "All Season"),
            formality = json.optString("formality", "Smart Casual"),
            conditionStatus = json.optString("conditionStatus", "Good"),
            keepDecision = json.optString("keepDecision", "Keep"),
            conditionNotes = json.optString("conditionNotes", "Clean structure and wearable finish."),
            ecoScore = json.optInt("ecoScore", 78).coerceIn(40, 98),
            stylingNote = json.optString("stylingNote", "Versatile wardrobe cornerstone."),
            webSourceSummary = resolvedWebSource
        )
    }

    suspend fun inspectGarmentCondition(
        item: WardrobeItem,
        bitmap: Bitmap?
    ): ConditionInspectionResult {
        val prompt = """
            You are a luxury garment conservationist and closet curator.
            Evaluate whether this wardrobe piece is in good condition and advise if the owner should Keep it, Repair / Dry Clean it, or Donate / Replace it.
            Item metadata:
            - Name: ${item.name} (${item.normalizedCategory()})
            - Material: ${item.materialType} (${item.fabric})
            - Wear count logged: ${item.wearCount} times
            - Current notes: ${item.notes.ifBlank { "None" }}
            ${if (bitmap != null) "Inspect the attached photo closely for fading, fraying, pilling, stains, creases, or shoe sole/scuff wear." else "Provide a realistic condition & care recommendation based on material durability and wear count."}
        """.trimIndent()

        val response = runWithDemandFallback(
            operationName = "inspectGarmentCondition",
            primaryModelName = FALLBACK_TEXT_MODEL,
            primaryModel = conditionFallbackModel,
            fallbackModelName = PRIMARY_TEXT_MODEL,
            fallbackModel = conditionPrimaryModel,
            primaryTimeoutMs = 16_000L,
            fallbackTimeoutMs = 28_000L
        ) { model ->
            if (bitmap != null) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            } else {
                model.generateContent(prompt)
            }
        }
        val raw = response.text ?: throw IllegalStateException("Condition inspection returned empty response.")
        val json = JSONObject(raw)
        return ConditionInspectionResult(
            conditionStatus = json.optString("conditionStatus", "Good"),
            keepDecision = json.optString("keepDecision", "Keep"),
            conditionNotes = json.optString("conditionNotes", "Wearable condition; routine care recommended.")
        )
    }

    suspend fun analyzeBodyStructureAndSkinTone(
        bodyPhoto: Bitmap?,
        profile: UserStyleProfile
    ): BodyScanAnalysisResult {
        val prompt = buildString {
            appendLine("You are a couture tailor and certified seasonal color analyst.")
            appendLine("Analyze this client's body structure, silhouette proportions, and skin tone match.")
            appendLine("Client baseline profile:")
            appendLine("- Gender identity: ${profile.gender}")
            appendLine("- Measurements: Height ${profile.heightCm}cm, Shoulder ${profile.shoulderCm}cm, Chest/Bust ${profile.chestCm}cm, Waist ${profile.waistCm}cm, Hip ${profile.hipCm}cm")
            appendLine("- Self-reported Body Type: ${profile.bodyType}, Skin Tone: ${profile.skinTone}, Undertone: ${profile.skinUndertone}")
            if (bodyPhoto != null) {
                appendLine("A full-body reference photo is attached. Use visual proportions and natural skin undertone in the photo to refine bodyType, skinTone, skinUndertone, and precise garment fit tailoring recommendations.")
            } else {
                appendLine("Use the exact anthropometric measurements above to compute body structure silhouette, garment drape guidelines, and skin-tone palette.")
            }
        }

        val response = runWithDemandFallback(
            operationName = "analyzeBodyStructure",
            primaryModelName = PRIMARY_TEXT_MODEL,
            primaryModel = bodyScanPrimaryModel,
            fallbackModelName = FALLBACK_TEXT_MODEL,
            fallbackModel = bodyScanFallbackModel,
            primaryTimeoutMs = 16_000L,
            fallbackTimeoutMs = 32_000L
        ) { model ->
            if (bodyPhoto != null) {
                model.generateContent(
                    content {
                        image(bodyPhoto)
                        text(prompt)
                    }
                )
            } else {
                model.generateContent(prompt)
            }
        }
        val raw = response.text ?: throw IllegalStateException("AI returned an empty body structure analysis.")
        val json = JSONObject(raw)
        return BodyScanAnalysisResult(
            bodyType = json.optString("bodyType", profile.bodyType),
            skinTone = json.optString("skinTone", profile.skinTone),
            skinUndertone = json.optString("skinUndertone", profile.skinUndertone),
            estimatedHeightCm = json.optInt("estimatedHeightCm", profile.heightCm).coerceIn(130, 220),
            fitSummary = json.optString("fitSummary", "Balanced tailoring accentuates natural lines."),
            detailedNotes = json.optString("detailedNotes", ""),
            bestColors = json.optJSONArray("bestColors").toStringList(),
            avoidColors = json.optJSONArray("avoidColors").toStringList(),
            flatteringCuts = json.optJSONArray("flatteringCuts").toStringList()
        )
    }

    suspend fun curateOccasionOutfits(
        profile: UserStyleProfile,
        wardrobe: List<WardrobeItem>,
        occasion: String
    ): List<AiOutfitRecommendation> {
        val wardrobeListing = if (wardrobe.isEmpty()) {
            "Wardrobe is currently empty; design 3 inspiring starter outfits tailored to this profile."
        } else {
            wardrobe.joinToString("\n") { item ->
                "- ${item.name} [${item.normalizedCategory()}, Color: ${item.colorName} (${item.colorHex}), Material: ${item.materialType} (${item.fabric}), Season: ${item.season}, Formality: ${item.formality}, Condition: ${item.conditionStatus}]"
            }
        }

        val prompt = """
            You are an elite personal fashion stylist for Atelier.
            Curate 3 distinct, wearable, 2026 trend-forward outfits for the occasion: "$occasion".
            Client Profile:
            - Gender: ${profile.gender}
            - Style Aesthetics: ${profile.stylePreferences.joinToString(", ")}
            - Body Structure: ${profile.bodyType} (${profile.heightCm}cm, Chest ${profile.chestCm}cm, Waist ${profile.waistCm}cm, Hip ${profile.hipCm}cm)
            - Skin Tone & Undertone: ${profile.skinTone} (${profile.skinUndertone} undertone)
            - Best Palette: ${profile.bestColors.joinToString(", ")}
            
            Client's Existing Virtual Wardrobe (prioritize mixing & matching these pieces first, including matching shoes):
            $wardrobeListing
            
            Ensure each outfit honors color theory, flatters the client's exact body structure and skin tone, and feels like an editorial lookbook rather than generic advice.
        """.trimIndent()

        val response = runWithDemandFallback(
            operationName = "curateOccasionOutfits",
            primaryModelName = FALLBACK_TEXT_MODEL,
            primaryModel = outfitStylistFallbackModel,
            fallbackModelName = PRIMARY_TEXT_MODEL,
            fallbackModel = outfitStylistPrimaryModel,
            primaryTimeoutMs = 18_000L,
            fallbackTimeoutMs = 30_000L
        ) { model ->
            model.generateContent(prompt)
        }
        val raw = response.text ?: throw IllegalStateException("AI returned empty outfit suggestions.")
        val json = JSONObject(raw)
        val array = json.optJSONArray("outfits") ?: JSONArray()
        return (0 until array.length()).map { idx ->
            val obj = array.getJSONObject(idx)
            AiOutfitRecommendation(
                title = obj.optString("title", "Curated Look ${idx + 1}"),
                occasion = obj.optString("occasion", occasion),
                itemsUsed = obj.optJSONArray("itemsUsed").toStringList(),
                trendTag = obj.optString("trendTag", "2026 Editorial"),
                colorTheoryNote = obj.optString("colorTheoryNote", ""),
                bodyFitAdvice = obj.optString("bodyFitAdvice", ""),
                skinToneNote = obj.optString("skinToneNote", ""),
                harmonyScore = obj.optInt("harmonyScore", 91).coerceIn(75, 99)
            )
        }
    }

    suspend fun generateStyleUpgradesAndEcoBrands(
        profile: UserStyleProfile,
        wardrobe: List<WardrobeItem>,
        focusOccasion: String
    ): DiscoverCuratedBundle {
        val wardrobeSummary = if (wardrobe.isEmpty()) {
            "No existing garments uploaded yet — recommend versatile capsule foundational pieces."
        } else {
            wardrobe.joinToString("; ") { "${it.name} (${it.colorName} ${it.materialType} ${it.normalizedCategory()}, ${it.keepDecision})" }
        }

        val prompt = """
            You are a sustainable fashion buyer & wardrobe strategist for Atelier (Year 2026).
            Analyze the user's EXISTING OLD CLOTHES and recommend what NEW pieces they should buy to dramatically upgrade their style while unlocking maximum combinations with their existing wardrobe.
            Also recommend 3 real sustainable/ethical fashion brands aligned to their gender, budget tier, and seasonal discounts.
            
            Client Profile:
            - Gender: ${profile.gender}
            - Aesthetics: ${profile.stylePreferences.joinToString(", ")}
            - Budget Tier: ${profile.budgetTier}
            - Body Structure: ${profile.bodyType}, Skin Tone: ${profile.skinTone} (${profile.skinUndertone} undertone)
            - Focus Occasion: $focusOccasion
            - Existing Wardrobe Items: $wardrobeSummary
            
            For each upgrade item, explicitly list which existing old clothes it pairs with in `pairsWithExisting`, explain why it upgrades their style and flatters their skin tone/body structure, and suggest a sustainable brand & seasonal sale hint.
        """.trimIndent()

        val response = runWithDemandFallback(
            operationName = "generateStyleUpgrades",
            primaryModelName = FALLBACK_TEXT_MODEL,
            primaryModel = discoverFallbackModel,
            fallbackModelName = PRIMARY_TEXT_MODEL,
            fallbackModel = discoverPrimaryModel,
            primaryTimeoutMs = 18_000L,
            fallbackTimeoutMs = 30_000L
        ) { model ->
            model.generateContent(prompt)
        }
        val raw = response.text ?: throw IllegalStateException("AI returned empty upgrade recommendations.")
        val json = JSONObject(raw)

        val upgradesArray = json.optJSONArray("upgrades") ?: JSONArray()
        val upgrades = (0 until upgradesArray.length()).map { i ->
            val obj = upgradesArray.getJSONObject(i)
            StyleUpgradeSuggestion(
                itemName = obj.optString("itemName", "Tailored Essential"),
                category = obj.optString("category", "Outerwear"),
                colorName = obj.optString("colorName", "Warm Sandstone"),
                pairsWithExisting = obj.optJSONArray("pairsWithExisting").toStringList(),
                whyItUpgrades = obj.optString("whyItUpgrades", ""),
                sustainableBrand = obj.optString("sustainableBrand", "Eileen Fisher"),
                ecoCertification = obj.optString("ecoCertification", "GOTS Organic"),
                estimatedPriceRange = obj.optString("estimatedPriceRange", "$90 - $160"),
                seasonalDealHint = obj.optString("seasonalDealHint", "Seasonal Archive 20% Off")
            )
        }

        val brandsArray = json.optJSONArray("sustainableBrands") ?: JSONArray()
        val brands = (0 until brandsArray.length()).map { i ->
            val obj = brandsArray.getJSONObject(i)
            SustainableBrandMatch(
                brandName = obj.optString("brandName", "Conscious Atelier"),
                specialty = obj.optString("specialty", "Organic Tailoring"),
                ecoCertification = obj.optString("ecoCertification", "B-Corp Certified"),
                seasonalOffer = obj.optString("seasonalOffer", "Winter Conscious Edit"),
                discountPercent = obj.optInt("discountPercent", 20).coerceIn(5, 60),
                whyItFitsUser = obj.optString("whyItFitsUser", "")
            )
        }

        return DiscoverCuratedBundle(
            trendHeadline = json.optString("trendHeadline", "2026 Tailored Craft & Conscious Capsule Silhouettes"),
            upgrades = upgrades,
            sustainableBrands = brands
        )
    }

    /**
     * Generates a high-fidelity 3D fashion studio virtual try-on render using gemini-nano-banana-2.1
     * with automatic failover to gemini-3.1-flash-lite-image during demand spikes.
     */
    suspend fun generate3dVirtualTryOn(
        profile: UserStyleProfile,
        selectedItems: List<WardrobeItem>,
        customOutfitDescription: String,
        occasion: String,
        angle: String,
        bodyReferenceBitmap: Bitmap?,
        garmentBitmaps: List<Bitmap>
    ): Bitmap {
        val outfitSpec = if (selectedItems.isNotEmpty()) {
            selectedItems.joinToString(", ") { "${it.colorName} ${it.fabric} ${it.name} (${it.normalizedCategory()})" }
        } else {
            customOutfitDescription.ifBlank { "Tailored warm ivory linen blazer over espresso pleated trousers" }
        }

        val angleInstruction = when (angle) {
            "3/4 Turn" -> "three-quarter dynamic editorial turn showing sleeve drape, waist tailoring, and profile silhouette"
            "Back Detail" -> "back three-quarter studio view showcasing shoulder seam tailoring, drape fall, and back silhouette"
            else -> "full-length centered front-facing editorial studio pose"
        }

        val prompt = """
            Create a hyper-realistic, high-fidelity 3D CGI fashion studio virtual try-on render on a minimalist Milanese atelier circular travertine pedestal.
            Subject Model Specifications:
            - Gender presentation: ${profile.gender}
            - Body structure & proportions: ${profile.bodyType}, height ${profile.heightCm}cm, chest ${profile.chestCm}cm, waist ${profile.waistCm}cm, hip ${profile.hipCm}cm
            - Skin tone: ${profile.skinTone} with ${profile.skinUndertone} undertone
            - Pose & Camera Angle: $angleInstruction, head-to-toe full body visible with realistic fabric physics, subtle natural drape creases, accurate tailored garment fit.
            - Outfit worn: $outfitSpec
            - Styled for occasion: $occasion
            - Studio Environment: Warm alabaster plaster cyclorama backdrop, soft sculptural architectural gallery lighting, subtle contact shadows on travertine floor.
            - Style: Ultra-crisp Clo3D / Unreal Engine 5 digital couture fashion visualization, zero text, zero watermarks, editorial luxury lookbook quality.
        """.trimIndent()

        val response = runWithDemandFallback(
            operationName = "generate3dVirtualTryOn",
            primaryModelName = PRIMARY_IMAGE_MODEL,
            primaryModel = tryOnPrimaryModel,
            fallbackModelName = FALLBACK_IMAGE_MODEL,
            fallbackModel = tryOnFallbackModel,
            primaryTimeoutMs = 32_000L,
            fallbackTimeoutMs = 28_000L
        ) { model ->
            model.generateContent(
                content {
                    if (bodyReferenceBitmap != null) {
                        image(bodyReferenceBitmap)
                    }
                    garmentBitmaps.take(3).forEach { bmp ->
                        image(bmp)
                    }
                    text(prompt)
                }
            )
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
            ?: throw IllegalStateException("3D Try-On renderer did not return an image. Please retry.")
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { idx ->
            optString(idx)?.takeIf { it.isNotBlank() }
        }
    }
}
