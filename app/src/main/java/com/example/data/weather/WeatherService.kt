package com.example.data.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

data class DailyWeatherForecast(
    val dateIso: String,
    val tempMaxC: Int,
    val tempMinC: Int,
    val precipitationProbPercent: Int,
    val weatherCode: Int,
    val conditionLabel: String
) {
    val avgTempC: Int get() = ((tempMaxC + tempMinC) / 2.0).roundToInt()
    val isRainLikely: Boolean get() = precipitationProbPercent >= 40 || weatherCode in setOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99)
}

data class WeatherForecastSnapshot(
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val currentTempC: Int,
    val feelsLikeC: Int,
    val precipitationProbPercent: Int,
    val windKmh: Int,
    val weatherCode: Int,
    val conditionLabel: String,
    val dailyForecasts: List<DailyWeatherForecast>
) {
    val isRainLikely: Boolean
        get() = precipitationProbPercent >= 40 || weatherCode in setOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99)

    fun forecastForDate(dateIso: String): DailyWeatherForecast? =
        dailyForecasts.firstOrNull { it.dateIso == dateIso }
}

class WeatherService {

    @SuppressLint("MissingPermission")
    fun readLastKnownCoarseLocation(context: Context): Pair<Double, Double>? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
                LocationManager.GPS_PROVIDER
            )
            var best: Location? = null
            for (provider in providers) {
                if (!lm.isProviderEnabled(provider)) continue
                val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
                if (best == null || loc.accuracy < best.accuracy) {
                    best = loc
                }
            }
            best?.let { it.latitude to it.longitude }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchWeatherByCity(cityQuery: String): WeatherForecastSnapshot = withContext(Dispatchers.IO) {
        val trimmed = cityQuery.trim().ifEmpty { "New Delhi" }
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
        val geoJson = JSONObject(httpGet(geoUrl))
        val results = geoJson.optJSONArray("results")
        if (results == null || results.length() == 0) {
            throw IllegalArgumentException("Could not find city \"$trimmed\". Try another city name.")
        }
        val first = results.getJSONObject(0)
        val lat = first.getDouble("latitude")
        val lon = first.getDouble("longitude")
        val name = first.optString("name", trimmed)
        val country = first.optString("country", "")
        val label = if (country.isNotBlank()) "$name, $country" else name
        fetchWeatherByCoordinates(lat, lon, label)
    }

    suspend fun fetchWeatherByCoordinates(
        latitude: Double,
        longitude: Double,
        locationLabel: String = "Local Weather"
    ): WeatherForecastSnapshot = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
            "&hourly=precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=14&timezone=auto"

        val root = JSONObject(httpGet(url))
        val current = root.getJSONObject("current")
        val currentTemp = current.optDouble("temperature_2m", 22.0).roundToInt()
        val feelsLike = current.optDouble("apparent_temperature", currentTemp.toDouble()).roundToInt()
        val wind = current.optDouble("wind_speed_10m", 8.0).roundToInt()
        val code = current.optInt("weather_code", 0)

        val hourlyProbs = root.optJSONObject("hourly")?.optJSONArray("precipitation_probability")
        val currentPrecipProb = if (hourlyProbs != null && hourlyProbs.length() > 0) {
            hourlyProbs.optInt(0, 0)
        } else {
            if (current.optDouble("precipitation", 0.0) > 0.0) 70 else 10
        }

        val dailyObj = root.optJSONObject("daily")
        val dailyList = mutableListOf<DailyWeatherForecast>()
        if (dailyObj != null) {
            val times = dailyObj.optJSONArray("time")
            val maxTemps = dailyObj.optJSONArray("temperature_2m_max")
            val minTemps = dailyObj.optJSONArray("temperature_2m_min")
            val precipMax = dailyObj.optJSONArray("precipitation_probability_max")
            val codes = dailyObj.optJSONArray("weather_code")
            val count = times?.length() ?: 0
            for (i in 0 until count) {
                val dCode = codes?.optInt(i, 0) ?: 0
                dailyList.add(
                    DailyWeatherForecast(
                        dateIso = times?.optString(i, "") ?: "",
                        tempMaxC = maxTemps?.optDouble(i, 24.0)?.roundToInt() ?: 24,
                        tempMinC = minTemps?.optDouble(i, 15.0)?.roundToInt() ?: 15,
                        precipitationProbPercent = precipMax?.optInt(i, 0) ?: 0,
                        weatherCode = dCode,
                        conditionLabel = describeWmoCode(dCode)
                    )
                )
            }
        }

        WeatherForecastSnapshot(
            locationName = locationLabel,
            latitude = latitude,
            longitude = longitude,
            currentTempC = currentTemp,
            feelsLikeC = feelsLike,
            precipitationProbPercent = currentPrecipProb.coerceIn(0, 100),
            windKmh = wind,
            weatherCode = code,
            conditionLabel = describeWmoCode(code),
            dailyForecasts = dailyList
        )
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Weather service returned HTTP $status.")
            }
            body
        } finally {
            conn.disconnect()
        }
    }

    private fun describeWmoCode(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Misty / Fog"
        51, 53, 55 -> "Light Drizzle"
        61, 63, 65 -> "Rain Showers"
        71, 73, 75, 77 -> "Snowfall"
        80, 81, 82 -> "Heavy Rain"
        95, 96, 99 -> "Thunderstorm"
        else -> "Mild Conditions"
    }
}
