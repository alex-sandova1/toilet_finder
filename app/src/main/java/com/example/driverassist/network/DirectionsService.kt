package com.example.driverassist.network

import android.util.Log
import com.example.driverassist.model.RouteDetails
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Decodes Google Maps polyline strings.
fun decodePolyline(encoded: String): List<LatLng> {
    val polyline = mutableListOf<LatLng>()
    var index = 0; var latitude = 0; var longitude = 0
    while (index < encoded.length) {
        var shift = 0; var result = 0; var value: Int
        do {
            value = encoded[index++].code - 63
            result = result or ((value and 0x1f) shl shift)
            shift += 5
        } while (value >= 0x20)
        latitude += if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        shift = 0; result = 0
        do {
            value = encoded[index++].code - 63
            result = result or ((value and 0x1f) shl shift)
            shift += 5
        } while (value >= 0x20)
        longitude += if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        polyline += LatLng(latitude / 1E5, longitude / 1E5)
    }
    return polyline
}

// Parses JSON into route data.
fun parseRouteDetails(responseBody: String): RouteDetails? {
    val responseJson = JSONObject(responseBody)
    if (responseJson.optString("status") != "OK") return null
    val route = responseJson.optJSONArray("routes")?.optJSONObject(0) ?: return null
    val encodedPolyline = route.optJSONObject("overview_polyline")?.optString("points").orEmpty()
    if (encodedPolyline.isBlank()) return null
    val leg = route.optJSONArray("legs")?.optJSONObject(0)
    return RouteDetails(
        points = decodePolyline(encodedPolyline),
        distanceText = leg?.optJSONObject("distance")?.optString("text") ?: "Distance unavailable",
        durationText = leg?.optJSONObject("duration")?.optString("text") ?: "Time unavailable"
    )
}

// Fetches walking directions from API.
suspend fun fetchWalkingRoute(apiKey: String, origin: LatLng, destination: LatLng): RouteDetails? = withContext(Dispatchers.IO) {
    val urlString = "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&mode=walking&key=$apiKey"
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10000; readTimeout = 10000
        }
        val body = if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText() }
        else connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        parseRouteDetails(body)
    } catch (e: Exception) {
        Log.e("Directions", "Failed: ${e.message}"); null
    } finally {
        connection?.disconnect()
    }
}
