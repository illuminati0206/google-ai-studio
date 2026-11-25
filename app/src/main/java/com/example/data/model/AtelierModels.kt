package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Structured Material Type taxonomy driving seasonal, thermal comfort,
 * breathability, and weather-adjusted outfit matching.
 */
enum class MaterialType(
    val displayName: String,
    val thermalRating: Int,        // 1 (ultra cool/airy) to 5 (heavy insulation)
    val breathability: String,
    val waterResistance: String,
    val idealSeasons: List<String>,
    val comfortNote: String
) {
    SILK(
        displayName = "Silk",
        thermalRating = 2,
        breathability = "High",
        waterResistance = "Low",
        idealSeasons = listOf("All Season", "Spring", "Autumn"),
        comfortNote = "Fluid drape & luminous sheen; protect from heavy rain"
    ),
    COTTON(
        displayName = "Cotton",
        thermalRating = 2,
        breathability = "High",
        waterResistance = "Low",
        idealSeasons = listOf("All Season", "Summer", "Spring"),
        comfortNote = "Everyday breathable comfort across mild to warm weather"
    ),
    LINEN(
        displayName = "Linen",
        thermalRating = 1,
        breathability = "Ultra High",
        waterResistance = "Low",
        idealSeasons = listOf("Summer", "Spring"),
        comfortNote = "Ultra-airy natural weave ideal for hot & humid days"
    ),
    WOOL(
        displayName = "Wool",
        thermalRating = 5,
        breathability = "Medium",
        waterResistance = "Medium",
        idealSeasons = listOf("Winter", "Autumn"),
        comfortNote = "Structured thermal warmth for crisp cold temperatures"
    ),
    CASHMERE(
        displayName = "Cashmere",
        thermalRating = 5,
        breathability = "Medium",
        waterResistance = "Low",
        idealSeasons = listOf("Winter", "Autumn"),
        comfortNote = "Featherlight luxury insulation for cold evenings"
    ),
    DENIM(
        displayName = "Denim",
        thermalRating = 3,
        breathability = "Medium",
        waterResistance = "Medium",
        idealSeasons = listOf("All Season", "Autumn", "Spring"),
        comfortNote = "Durable mid-weight twill suited for transitional seasons"
    ),
    LEATHER(
        displayName = "Leather",
        thermalRating = 4,
        breathability = "Low",
        waterResistance = "High",
        idealSeasons = listOf("Autumn", "Winter", "Monsoon"),
        comfortNote = "Wind-blocking & rain-resilient outer/shoe protection"
    ),
    VELVET(
        displayName = "Velvet",
        thermalRating = 4,
        breathability = "Low",
        waterResistance = "Low",
        idealSeasons = listOf("Winter", "Autumn"),
        comfortNote = "Plush festive evening texture best in cool weather"
    ),
    SYNTHETIC_BLEND(
        displayName = "Synthetic Blend",
        thermalRating = 3,
        breathability = "Medium",
        waterResistance = "High",
        idealSeasons = listOf("All Season", "Monsoon", "Autumn"),
        comfortNote = "Quick-drying & wrinkle-resistant for rain or travel"
    );

    companion object {
        val displayNames: List<String> = entries.map { it.displayName }

        fun fromRaw(raw: String?): MaterialType {
            if (raw.isNullOrBlank()) return COTTON
            val lower = raw.trim().lowercase()
            return entries.firstOrNull {
                it.displayName.lowercase() == lower ||
                    it.name.lowercase() == lower ||
                    lower.contains(it.displayName.lowercase().substringBefore(" "))
            } ?: COTTON
        }
    }
}

// --- Reference Enumerations & Constants (1:1 Parity with firestore.rules) ---

object AtelierConstants {
    val GENDERS = listOf("Women", "Men", "Non-Binary")
    val BUDGET_TIERS = listOf("Accessible", "Contemporary", "Luxury", "Sustainable First")
    val STYLE_AESTHETICS = listOf(
        "Minimalist",
        "Classic Tailored",
        "Ethnic & Festive",
        "Streetwear",
        "Smart Casual",
        "Quiet Luxury",
        "Bohemian Craft",
        "Avant-Garde"
    )
    val GARMENT_CATEGORIES = listOf(
        "Top",
        "Bottom",
        "Outerwear",
        "Dress / Suit",
        "Ethnic / Festive",
        "Shoes",
        "Accessory"
    )
    val MATERIAL_TYPES = MaterialType.displayNames
    val CONDITION_STATUSES = listOf(
        "Mint",
        "Good",
        "Fair / Needs Care",
        "Worn / Damaged"
    )
    val KEEP_DECISIONS = listOf(
        "Keep",
        "Repair / Dry Clean",
        "Donate / Replace"
    )
    val SEASONS = listOf("All Season", "Spring", "Summer", "Autumn", "Winter", "Monsoon")
    val FORMALITIES = listOf(
        "Casual",
        "Smart Casual",
        "Office / Corporate",
        "Party / Evening",
        "Wedding / Festive"
    )
    val OCCASIONS = listOf(
        "Wedding / Marriage",
        "Office / Corporate",
        "Party & Evening",
        "Casual Everyday",
        "Festive & Ethnic",
        "Date Night",
        "Travel & Resort"
    )
    val BODY_TYPES = listOf(
        "Hourglass",
        "Athletic / Rectangle",
        "Inverted Triangle",
        "Pear / Triangle",
        "Trapezoid / Broad",
        "Oval / Soft"
    )
    val SKIN_TONES = listOf("Fair", "Light-Medium", "Medium", "Olive", "Tan / Wheatish", "Deep")
    val SKIN_UNDERTONES = listOf("Warm", "Cool", "Neutral", "Olive")
    val TRY_ON_ANGLES = listOf("Front", "3/4 Turn", "Back Detail")
    val REMINDER_LEAD_OPTIONS = listOf(
        1440 to "24 hours before",
        720 to "12 hours before",
        120 to "2 hours before",
        30 to "30 min before"
    )
}

// --- Firestore Durable Models (@Keep + @IgnoreExtraProperties + No-Arg Defaults + No `is`-prefixed Booleans) ---

@Keep
@IgnoreExtraProperties
data class UserStyleProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val gender: String = "Women",
    val stylePreferences: List<String> = listOf("Minimalist", "Classic Tailored"),
    val budgetTier: String = "Contemporary",
    val bodyType: String = "Athletic / Rectangle",
    val heightCm: Int = 170,
    val chestCm: Int = 92,
    val waistCm: Int = 74,
    val hipCm: Int = 96,
    val shoulderCm: Int = 42,
    val skinTone: String = "Medium",
    val skinUndertone: String = "Warm",
    val bestColors: List<String> = listOf("Warm Ivory", "Terracotta", "Olive Sage", "Champagne"),
    val avoidColors: List<String> = listOf("Harsh Neon Yellow", "Icy Silver"),
    val fitSummary: String = "",
    val bodyAnalysisNotes: String = "",
    val bodyScanLocalPath: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WardrobeItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = "Top",
    val colorName: String = "Ivory",
    val colorHex: String = "#F5EFE6",
    val materialType: String = "Cotton",
    val fabric: String = "Cotton",
    val season: String = "All Season",
    val formality: String = "Smart Casual",
    val conditionStatus: String = "Good",
    val keepDecision: String = "Keep",
    val conditionNotes: String = "",
    val brand: String = "",
    val localImagePath: String = "",
    val ecoScore: Int = 75,
    val wearCount: Int = 0,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedMaterial(): MaterialType = MaterialType.fromRaw(materialType.ifBlank { fabric })
    fun normalizedCategory(): String = if (category == "Footwear") "Shoes" else category
}

@Keep
@IgnoreExtraProperties
data class SavedOutfit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val occasion: String = "Smart Casual",
    val itemIds: List<String> = emptyList(),
    val itemSummary: String = "",
    val colorHarmonyScore: Int = 88,
    val fitScore: Int = 90,
    val stylingNotes: String = "",
    val tryOnImagePath: String = "",
    val sourceType: String = "MIX_MATCH",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PlannedOutfitEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventTitle: String = "",
    val occasion: String = "Office / Corporate",
    val dateIso: String = "",
    val timeLabel: String = "10:00",
    val outfitId: String = "",
    val outfitTitle: String = "",
    val reminderLeadMinutes: Int = 1440,
    val reminderEnabled: Boolean = true,
    val reminderEpochMillis: Long = 0L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DiscountAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemName: String = "",
    val brandName: String = "",
    val category: String = "Outerwear",
    val targetDiscountPercent: Int = 25,
    val currentDiscountPercent: Int = 15,
    val ecoCertified: Boolean = true,
    val activeAlert: Boolean = true,
    val seasonalTag: String = "Winter Atelier Edit",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// --- AI Structured Response Models ---

data class GarmentAutoTagResult(
    val name: String,
    val brand: String = "",
    val category: String,
    val colorName: String,
    val colorHex: String,
    val materialType: String,
    val fabric: String,
    val season: String,
    val formality: String,
    val conditionStatus: String,
    val keepDecision: String,
    val conditionNotes: String,
    val ecoScore: Int,
    val stylingNote: String,
    val webSourceSummary: String = ""
)

data class ConditionInspectionResult(
    val conditionStatus: String,
    val keepDecision: String,
    val conditionNotes: String
)

data class BodyScanAnalysisResult(
    val bodyType: String,
    val skinTone: String,
    val skinUndertone: String,
    val estimatedHeightCm: Int,
    val fitSummary: String,
    val detailedNotes: String,
    val bestColors: List<String>,
    val avoidColors: List<String>,
    val flatteringCuts: List<String>
)

data class AiOutfitRecommendation(
    val title: String,
    val occasion: String,
    val itemsUsed: List<String>,
    val trendTag: String,
    val colorTheoryNote: String,
    val bodyFitAdvice: String,
    val skinToneNote: String,
    val harmonyScore: Int
)

data class StyleUpgradeSuggestion(
    val itemName: String,
    val category: String,
    val colorName: String,
    val pairsWithExisting: List<String>,
    val whyItUpgrades: String,
    val sustainableBrand: String,
    val ecoCertification: String,
    val estimatedPriceRange: String,
    val seasonalDealHint: String
)

data class SustainableBrandMatch(
    val brandName: String,
    val specialty: String,
    val ecoCertification: String,
    val seasonalOffer: String,
    val discountPercent: Int,
    val whyItFitsUser: String
)

data class DiscoverCuratedBundle(
    val trendHeadline: String,
    val upgrades: List<StyleUpgradeSuggestion>,
    val sustainableBrands: List<SustainableBrandMatch>
)

// --- Generic UI State ---

sealed interface AsyncUiState<out T> {
    data object Idle : AsyncUiState<Nothing>
    data object Loading : AsyncUiState<Nothing>
    data class Success<T>(val data: T) : AsyncUiState<T>
    data class Error(val message: String) : AsyncUiState<Nothing>
}
