package com.example.domain

import com.example.data.model.MaterialType
import com.example.data.model.UserStyleProfile
import com.example.data.model.WardrobeItem
import com.example.data.weather.DailyWeatherForecast
import com.example.data.weather.WeatherForecastSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic Color Theory, Material Thermal Comfort, Weather Forecast,
 * Shoes Pairing, Condition Health, and 2026 Fashion Trend Outfit Compatibility Engine.
 */
data class CompatibleOutfitProposal(
    val title: String,
    val items: List<WardrobeItem>,
    val colorHarmonyType: String,
    val colorHarmonyScore: Int,
    val fitAndUndertoneScore: Int,
    val materialComfortScore: Int,
    val trendAlignmentTag: String,
    val weatherNote: String,
    val overallScore: Int,
    val rationale: String
)

data class EventWeatherAdjustment(
    val forecast: DailyWeatherForecast,
    val advisoryHeadline: String,
    val adjustmentTip: String,
    val recommendedMaterials: List<String>,
    val suggestedWardrobeOutfit: CompatibleOutfitProposal?
)

data class ColorHsl(
    val hue: Float,        // 0..360
    val saturation: Float, // 0..1
    val lightness: Float   // 0..1
) {
    val isNeutral: Boolean
        get() = saturation < 0.16f || lightness < 0.14f || lightness > 0.90f

    val isWarmHue: Boolean
        get() = !isNeutral && (hue <= 70f || hue >= 320f)
}

object WardrobeCompatibilityEngine {

    private val formalityRank = mapOf(
        "Casual" to 1,
        "Smart Casual" to 2,
        "Office / Corporate" to 3,
        "Party / Evening" to 4,
        "Wedding / Festive" to 5
    )

    fun parseHexToHsl(hex: String): ColorHsl {
        val clean = hex.trim().removePrefix("#")
        val parsed = clean.toLongOrNull(16) ?: 0xD8CFC2L
        val r = ((parsed shr 16) and 0xFF) / 255f
        val g = ((parsed shr 8) and 0xFF) / 255f
        val b = (parsed and 0xFF) / 255f

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        val l = (maxC + minC) / 2f

        val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f)).coerceAtLeast(0.001f)
        val h = when {
            delta == 0f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        return ColorHsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    fun evaluateColorPair(hexA: String, hexB: String): Pair<String, Int> {
        val a = parseHexToHsl(hexA)
        val b = parseHexToHsl(hexB)

        if (a.isNeutral && b.isNeutral) {
            val contrast = abs(a.lightness - b.lightness)
            return if (contrast >= 0.22f) {
                "Quiet Luxury Tonal Neutral" to 96
            } else {
                "Soft Monochromatic Neutral" to 91
            }
        }
        if (a.isNeutral || b.isNeutral) {
            return "Neutral-Anchored Accent" to 94
        }

        val rawDiff = abs(a.hue - b.hue)
        val hueDiff = min(rawDiff, 360f - rawDiff)

        return when {
            hueDiff <= 15f -> "Monochromatic Tonal" to 93
            hueDiff <= 38f -> "Analogous Harmony" to 90
            hueDiff in 150f..210f -> "Complementary Statement" to 88
            hueDiff in 105f..135f -> "Triadic Editorial Balance" to 85
            else -> "Contrasting Pair" to 72
        }
    }

    fun evaluateCombination(
        items: List<WardrobeItem>,
        profile: UserStyleProfile,
        targetSeason: String = "All Season",
        targetOccasion: String = "",
        temperatureC: Int? = null,
        precipitationProbPercent: Int? = null
    ): CompatibleOutfitProposal {
        if (items.isEmpty()) {
            return CompatibleOutfitProposal(
                title = "Empty Canvas",
                items = emptyList(),
                colorHarmonyType = "Select items",
                colorHarmonyScore = 0,
                fitAndUndertoneScore = 0,
                materialComfortScore = 0,
                trendAlignmentTag = "Awaiting Selection",
                weatherNote = "",
                overallScore = 0,
                rationale = "Select at least two wardrobe pieces (including shoes) to compute color theory, material comfort & trend compatibility."
            )
        }

        // 1. Pairwise Color Theory Score
        val pairEvaluations = mutableListOf<Pair<String, Int>>()
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                pairEvaluations.add(evaluateColorPair(items[i].colorHex, items[j].colorHex))
            }
        }
        val colorScore = if (pairEvaluations.isEmpty()) 88 else pairEvaluations.map { it.second }.average().toInt()
        val dominantHarmony = pairEvaluations.maxByOrNull { it.second }?.first ?: "Monochromatic Anchor"

        // 2. Formality Coherence Score
        val ranks = items.map { formalityRank[it.formality] ?: 2 }
        val spread = (ranks.maxOrNull() ?: 2) - (ranks.minOrNull() ?: 2)
        val formalityScore = when (spread) {
            0 -> 96
            1 -> 90
            2 -> 78
            else -> 62
        }

        // 3. Material Thermal Comfort + Weather Forecast Score
        val (materialScore, weatherNote) = computeMaterialAndWeatherScore(
            items = items,
            targetSeason = targetSeason,
            temperatureC = temperatureC,
            precipitationProbPercent = precipitationProbPercent
        )

        // 4. Skin-Tone Undertone Match
        val undertoneScore = computeUndertoneFitScore(items, profile.skinUndertone)

        // 5. Condition Health & Shoes Completeness Bonus
        val hasShoes = items.any { it.normalizedCategory() == "Shoes" }
        val hasWornItem = items.any { it.keepDecision == "Donate / Replace" || it.conditionStatus == "Worn / Damaged" }
        val completenessBonus = (if (hasShoes) 3 else 0) - (if (hasWornItem) 12 else 0)

        // 6. 2026 Fashion Trend Alignment
        val (trendTag, trendBonus) = detectTrendAlignment(items, dominantHarmony, targetOccasion)

        val overall = (
            colorScore * 0.32 +
                formalityScore * 0.20 +
                materialScore * 0.26 +
                undertoneScore * 0.22 +
                completenessBonus + trendBonus
            ).toInt().coerceIn(45, 99)

        val rationale = buildString {
            append("$dominantHarmony ($colorScore%)")
            append(" • Material comfort $materialScore%")
            if (hasShoes) append(" • Shoes matched")
            if (hasWornItem) append(" • Includes item flagged for replacement")
            if (weatherNote.isNotBlank()) append(" • $weatherNote")
            else append(" • Tailored for ${profile.skinUndertone.lowercase()} undertone & ${profile.bodyType}.")
        }

        return CompatibleOutfitProposal(
            title = buildProposalTitle(items, trendTag),
            items = items,
            colorHarmonyType = dominantHarmony,
            colorHarmonyScore = colorScore,
            fitAndUndertoneScore = undertoneScore,
            materialComfortScore = materialScore,
            trendAlignmentTag = trendTag,
            weatherNote = weatherNote,
            overallScore = overall,
            rationale = rationale
        )
    }

    /**
     * Generates top compatible outfits from wearable (non-retired) wardrobe items + shoes,
     * factoring in MaterialType comfort, Color Theory, Formality, Trends, and optional live weather.
     */
    fun generateCompatibleOutfits(
        wardrobe: List<WardrobeItem>,
        profile: UserStyleProfile,
        occasion: String = "All",
        season: String = "All Season",
        temperatureC: Int? = null,
        precipitationProbPercent: Int? = null,
        maxResults: Int = 5
    ): List<CompatibleOutfitProposal> {
        // Prioritize items in wearable condition (Keep or Repair), falling back to full list if user has < 2 items
        val wearable = wardrobe.filter { it.keepDecision != "Donate / Replace" }
        val pool = if (wearable.size >= 2) wearable else wardrobe
        if (pool.size < 2) return emptyList()

        val tops = pool.filter { it.normalizedCategory() == "Top" }
        val bottoms = pool.filter { it.normalizedCategory() == "Bottom" }
        val onePieces = pool.filter { it.normalizedCategory() == "Dress / Suit" || it.normalizedCategory() == "Ethnic / Festive" }
        val outerwear = pool.filter { it.normalizedCategory() == "Outerwear" }
        val shoes = pool.filter { it.normalizedCategory() == "Shoes" }
        val accessories = pool.filter { it.normalizedCategory() == "Accessory" }

        val needsLayer = (temperatureC != null && temperatureC <= 18) || (precipitationProbPercent != null && precipitationProbPercent >= 45)
        val isHot = temperatureC != null && temperatureC >= 28

        val candidates = mutableListOf<List<WardrobeItem>>()

        for (top in tops.take(8)) {
            for (bottom in bottoms.take(8)) {
                val base = mutableListOf(top, bottom)
                if (!isHot) {
                    val bestOuter = outerwear.maxByOrNull { outer ->
                        val colorMatch = evaluateColorPair(outer.colorHex, top.colorHex).second
                        val weatherFit = scoreSingleItemWeather(outer, temperatureC, precipitationProbPercent)
                        colorMatch + weatherFit
                    }
                    if (bestOuter != null && (needsLayer || temperatureC == null)) {
                        base.add(bestOuter)
                    }
                }
                shoes.maxByOrNull { shoe ->
                    evaluateColorPair(shoe.colorHex, bottom.colorHex).second +
                        scoreSingleItemWeather(shoe, temperatureC, precipitationProbPercent)
                }?.let { base.add(it) }
                accessories.firstOrNull()?.let { base.add(it) }
                candidates.add(base)
            }
        }

        for (hero in onePieces.take(8)) {
            val base = mutableListOf(hero)
            if (!isHot) {
                outerwear.maxByOrNull { evaluateColorPair(it.colorHex, hero.colorHex).second }?.let { base.add(it) }
            }
            shoes.maxByOrNull { shoe ->
                evaluateColorPair(shoe.colorHex, hero.colorHex).second +
                    scoreSingleItemWeather(shoe, temperatureC, precipitationProbPercent)
            }?.let { base.add(it) }
            accessories.maxByOrNull { evaluateColorPair(it.colorHex, hero.colorHex).second }?.let { base.add(it) }
            if (base.size >= 2) {
                candidates.add(base)
            }
        }

        if (candidates.isEmpty()) {
            for (i in pool.indices) {
                for (j in i + 1 until pool.size) {
                    if (pool[i].normalizedCategory() != pool[j].normalizedCategory()) {
                        candidates.add(listOf(pool[i], pool[j]))
                    }
                }
            }
        }

        return candidates
            .distinctBy { list -> list.map { it.id }.sorted() }
            .map { combo ->
                var evaluated = evaluateCombination(
                    items = combo,
                    profile = profile,
                    targetSeason = season,
                    targetOccasion = occasion,
                    temperatureC = temperatureC,
                    precipitationProbPercent = precipitationProbPercent
                )
                if (occasion != "All" && occasion.isNotBlank()) {
                    val occasionBoost = computeOccasionMatchBoost(combo, occasion)
                    evaluated = evaluated.copy(
                        overallScore = (evaluated.overallScore + occasionBoost).coerceIn(45, 99)
                    )
                }
                evaluated
            }
            .sortedByDescending { it.overallScore }
            .take(maxResults)
    }

    /**
     * Generates live weather-adjusted outfit recommendations from the user's wardrobe.
     */
    fun generateWeatherAdjustedOutfits(
        wardrobe: List<WardrobeItem>,
        profile: UserStyleProfile,
        weather: WeatherForecastSnapshot,
        maxResults: Int = 3
    ): List<CompatibleOutfitProposal> {
        val inferredSeason = when {
            weather.isRainLikely -> "Monsoon"
            weather.currentTempC >= 27 -> "Summer"
            weather.currentTempC <= 15 -> "Winter"
            weather.currentTempC <= 21 -> "Autumn"
            else -> "Spring"
        }
        return generateCompatibleOutfits(
            wardrobe = wardrobe,
            profile = profile,
            season = inferredSeason,
            temperatureC = weather.currentTempC,
            precipitationProbPercent = weather.precipitationProbPercent,
            maxResults = maxResults
        )
    }

    /**
     * Automatically adjusts outfit advice for a scheduled Event Planner item using
     * the live daily forecast for that event's exact date.
     */
    fun evaluateEventWeatherAdjustment(
        forecast: DailyWeatherForecast,
        occasion: String,
        wardrobe: List<WardrobeItem>,
        profile: UserStyleProfile
    ): EventWeatherAdjustment {
        val avgTemp = forecast.avgTempC
        val isRain = forecast.isRainLikely

        val recommendedMaterials = when {
            isRain && avgTemp <= 17 -> listOf("Wool", "Leather", "Synthetic Blend")
            isRain -> listOf("Synthetic Blend", "Cotton", "Leather")
            avgTemp >= 28 -> listOf("Linen", "Cotton", "Silk")
            avgTemp <= 15 -> listOf("Wool", "Cashmere", "Velvet", "Leather")
            else -> listOf("Cotton", "Silk", "Denim")
        }

        val advisoryHeadline = when {
            isRain -> "Rain Alert (${forecast.precipitationProbPercent}% chance) • ${forecast.tempMinC}°–${forecast.tempMaxC}°C"
            avgTemp >= 28 -> "Warm Forecast (${forecast.tempMaxC}°C high) • Breathable Edit"
            avgTemp <= 15 -> "Cold Forecast (${forecast.tempMinC}°C low) • Layered Warmth"
            else -> "Pleasant ${forecast.conditionLabel} • ${forecast.tempMinC}°–${forecast.tempMaxC}°C"
        }

        val adjustmentTip = when {
            isRain -> "Swap delicate raw Silk or suede shoes for water-resistant Leather or Synthetic Blend outerwear & closed shoes."
            avgTemp >= 28 -> "Prioritize ultra-breathable Linen or Cotton pieces and skip heavy outerwear to stay comfortable."
            avgTemp <= 15 -> "Add a structured Wool or Cashmere outerwear layer and enclosed Leather shoes for thermal insulation."
            else -> "Ideal mid-weight weather—balance Cotton, Silk, or tailored Denim with optional light layering."
        }

        val bestOutfit = generateCompatibleOutfits(
            wardrobe = wardrobe,
            profile = profile,
            occasion = occasion,
            temperatureC = avgTemp,
            precipitationProbPercent = forecast.precipitationProbPercent,
            maxResults = 1
        ).firstOrNull()

        return EventWeatherAdjustment(
            forecast = forecast,
            advisoryHeadline = advisoryHeadline,
            adjustmentTip = adjustmentTip,
            recommendedMaterials = recommendedMaterials,
            suggestedWardrobeOutfit = bestOutfit
        )
    }

    private fun computeMaterialAndWeatherScore(
        items: List<WardrobeItem>,
        targetSeason: String,
        temperatureC: Int?,
        precipitationProbPercent: Int?
    ): Pair<Int, String> {
        val materials = items.map { it.resolvedMaterial() }
        val distinctMaterials = materials.toSet()
        val textureBonus = if (distinctMaterials.size >= 2) 5 else 0

        if (temperatureC != null) {
            val itemScores = items.map { scoreSingleItemWeather(it, temperatureC, precipitationProbPercent) }
            val avg = (itemScores.average().toInt() + textureBonus).coerceIn(55, 99)
            val rain = (precipitationProbPercent ?: 0) >= 40
            val note = when {
                rain -> "Weather-checked for ${temperatureC}°C & ${precipitationProbPercent}% rain"
                temperatureC >= 28 -> "Breathable comfort for warm ${temperatureC}°C"
                temperatureC <= 15 -> "Insulated warmth for cool ${temperatureC}°C"
                else -> "Balanced thermal comfort for ${temperatureC}°C"
            }
            return avg to note
        }

        val seasonMatches = items.count { item ->
            val mat = item.resolvedMaterial()
            item.season == "All Season" ||
                targetSeason == "All Season" ||
                item.season == targetSeason ||
                targetSeason in mat.idealSeasons
        }
        val base = ((seasonMatches.toFloat() / items.size) * 92f).toInt() + textureBonus
        val comfortText = if (distinctMaterials.size >= 2) {
            "Rich ${distinctMaterials.take(2).joinToString(" + ") { it.displayName }} texture balance"
        } else {
            materials.first().comfortNote
        }
        return base.coerceIn(65, 98) to comfortText
    }

    private fun scoreSingleItemWeather(
        item: WardrobeItem,
        temperatureC: Int?,
        precipitationProbPercent: Int?
    ): Int {
        if (temperatureC == null) return 85
        val mat = item.resolvedMaterial()
        val isRain = (precipitationProbPercent ?: 0) >= 40

        var score = when {
            temperatureC >= 28 -> when (mat.thermalRating) {
                1 -> 98
                2 -> 92
                3 -> 78
                else -> 58
            }
            temperatureC in 18..27 -> when (mat.thermalRating) {
                2, 3 -> 96
                1, 4 -> 86
                else -> 72
            }
            else -> when (mat.thermalRating) {
                4, 5 -> 97
                3 -> 85
                else -> 68
            }
        }

        if (isRain) {
            score += when (mat) {
                MaterialType.LEATHER, MaterialType.SYNTHETIC_BLEND -> 8
                MaterialType.WOOL -> 2
                MaterialType.SILK, MaterialType.VELVET, MaterialType.CASHMERE -> -14
                else -> 0
            }
        }
        return score.coerceIn(45, 99)
    }

    private fun computeUndertoneFitScore(items: List<WardrobeItem>, undertone: String): Int {
        val scores = items.map { item ->
            val hsl = parseHexToHsl(item.colorHex)
            when {
                hsl.isNeutral -> 92
                undertone.equals("Warm", ignoreCase = true) || undertone.equals("Olive", ignoreCase = true) ->
                    if (hsl.isWarmHue || (hsl.hue in 80f..160f)) 95 else 78
                undertone.equals("Cool", ignoreCase = true) ->
                    if (!hsl.isWarmHue || hsl.hue in 300f..355f) 95 else 78
                else -> 91
            }
        }
        return scores.average().toInt().coerceIn(70, 98)
    }

    private fun detectTrendAlignment(
        items: List<WardrobeItem>,
        harmony: String,
        occasion: String
    ): Pair<String, Int> {
        val materials = items.map { it.resolvedMaterial() }.toSet()
        val hasEthnic = items.any { it.normalizedCategory() == "Ethnic / Festive" }
        val hasOuterwear = items.any { it.normalizedCategory() == "Outerwear" }
        val hasLuxuryMaterial = MaterialType.SILK in materials || MaterialType.CASHMERE in materials || MaterialType.WOOL in materials
        val hasEcoCraft = items.any { it.ecoScore >= 82 } || MaterialType.LINEN in materials

        return when {
            hasEthnic && hasOuterwear -> "2026 Modern Heritage Layering" to 5
            harmony.contains("Tonal") || harmony.contains("Monochromatic") -> "Quiet Luxury Monochrome" to 5
            hasEcoCraft && items.size >= 2 -> "Tactile Sustainable Craft" to 4
            hasLuxuryMaterial && occasion.contains("Wedding", ignoreCase = true) -> "Sculptural Festive Tailoring" to 5
            harmony.contains("Complementary") -> "Editorial Color-Block Accent" to 4
            else -> "Contemporary Capsule Edit" to 3
        }
    }

    private fun computeOccasionMatchBoost(items: List<WardrobeItem>, occasion: String): Int {
        val lower = occasion.lowercase()
        val matchCount = items.count { item ->
            val form = item.formality.lowercase()
            val cat = item.normalizedCategory().lowercase()
            when {
                lower.contains("wedding") || lower.contains("marriage") || lower.contains("festive") ->
                    form.contains("wedding") || form.contains("festive") || cat.contains("ethnic")
                lower.contains("office") || lower.contains("corporate") ->
                    form.contains("office") || form.contains("smart") || cat.contains("outerwear")
                lower.contains("party") || lower.contains("evening") || lower.contains("date") ->
                    form.contains("party") || form.contains("evening") || form.contains("smart")
                else -> form.contains("casual") || form.contains("smart")
            }
        }
        return if (matchCount > 0) 6 else -4
    }

    private fun buildProposalTitle(items: List<WardrobeItem>, trendTag: String): String {
        val lead = items.firstOrNull() ?: return trendTag
        return "${lead.colorName} ${lead.resolvedMaterial().displayName} • ${trendTag.substringBefore(" ")}"
    }
}
