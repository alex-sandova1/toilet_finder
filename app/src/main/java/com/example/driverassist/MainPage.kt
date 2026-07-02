package com.example.driverassist

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.driverassist.ui.theme.DriverAssistTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

data class RestroomReview(
    val placeId: String,
    val cleanlinessRating: Int,
    val codeRequired: Boolean?,
    val hoursText: String,
    val reviewText: String
)

data class RouteDetails(
    val points: List<LatLng>,
    val distanceText: String,
    val durationText: String
)

private fun buildLocalReview(place: Place): RestroomReview {
    val key = place.id ?: place.displayName ?: "unknown"
    val seed = abs(key.hashCode())
    val cleanliness = (seed % 5) + 1
    val codeRequired = when (seed % 3) {
        0 -> true
        1 -> false
        else -> null
    }
    val hoursText = listOf(
        "Open 24 hours",
        "6:00 AM - 10:00 PM",
        "7:00 AM - 11:00 PM",
        "Hours not listed"
    )[seed % 4]
    val reviewText = when (cleanliness) {
        5 -> "Very clean and regularly stocked."
        4 -> "Clean most visits with minor wait times."
        3 -> "Average cleanliness; bring hand sanitizer."
        2 -> "Below average cleanliness; use only if needed."
        else -> "Poor cleanliness based on recent reports."
    }

    return RestroomReview(
        placeId = key,
        cleanlinessRating = cleanliness,
        codeRequired = codeRequired,
        hoursText = hoursText,
        reviewText = reviewText
    )
}

private fun codeRequiredLabel(codeRequired: Boolean?): String {
    return when (codeRequired) {
        true -> "Yes"
        false -> "No"
        null -> "Unknown"
    }
}

private fun distanceMeters(from: LatLng, to: LatLng): Double {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(
        from.latitude,
        from.longitude,
        to.latitude,
        to.longitude,
        results
    )
    return results[0].toDouble()
}

private fun findNearestBathroom(from: LatLng, places: List<Place>): Place? {
    var nearest: Place? = null
    var nearestDistance = Double.MAX_VALUE

    places.forEach { place ->
        val location = place.location ?: return@forEach
        val distance = distanceMeters(from, location)
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearest = place
        }
    }

    return nearest
}

@Suppress("DEPRECATION")
private fun resolveMapsApiKey(context: Context): String? {
    val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        )
    } else {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    }

    return applicationInfo.metaData?.getString("com.google.android.geo.API_KEY")?.takeIf { it.isNotBlank() }
}

private fun decodePolyline(encoded: String): List<LatLng> {
    val polyline = mutableListOf<LatLng>()
    var index = 0
    var latitude = 0
    var longitude = 0

    while (index < encoded.length) {
        var shift = 0
        var result = 0
        var value: Int
        do {
            value = encoded[index++].code - 63
            result = result or ((value and 0x1f) shl shift)
            shift += 5
        } while (value >= 0x20)
        latitude += if ((result and 1) != 0) (result shr 1).inv() else result shr 1

        shift = 0
        result = 0
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

private fun parseRouteDetails(responseBody: String): RouteDetails? {
    val responseJson = JSONObject(responseBody)
    val status = responseJson.optString("status")
    if (status != "OK") {
        Log.e("BathroomRoute", "Directions request failed with status: $status")
        return null
    }

    val route = responseJson.optJSONArray("routes")?.optJSONObject(0) ?: return null
    val encodedPolyline = route.optJSONObject("overview_polyline")?.optString("points").orEmpty()
    if (encodedPolyline.isBlank()) return null

    val leg = route.optJSONArray("legs")?.optJSONObject(0)
    val distanceText = leg?.optJSONObject("distance")?.optString("text").orEmpty()
    val durationText = leg?.optJSONObject("duration")?.optString("text").orEmpty()

    return RouteDetails(
        points = decodePolyline(encodedPolyline),
        distanceText = distanceText.ifBlank { "Distance unavailable" },
        durationText = durationText.ifBlank { "Time unavailable" }
    )
}

private suspend fun fetchWalkingRoute(
    apiKey: String,
    origin: LatLng,
    destination: LatLng
): RouteDetails? = withContext(Dispatchers.IO) {
    val urlString = android.net.Uri.Builder()
        .scheme("https")
        .authority("maps.googleapis.com")
        .appendPath("maps")
        .appendPath("api")
        .appendPath("directions")
        .appendPath("json")
        .appendQueryParameter("origin", "${origin.latitude},${origin.longitude}")
        .appendQueryParameter("destination", "${destination.latitude},${destination.longitude}")
        .appendQueryParameter("mode", "walking")
        .appendQueryParameter("key", apiKey)
        .build()
        .toString()

    var connection: HttpURLConnection? = null
    try {
        connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }

        val body = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }

        parseRouteDetails(body)
    } catch (exception: Exception) {
        Log.e("BathroomRoute", "Failed to fetch route: ${exception.message}", exception)
        null
    } finally {
        connection?.disconnect()
    }
}

class MainPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the New Places SDK
        val apiKey = resolveMapsApiKey(this)
        if (!Places.isInitialized() && !apiKey.isNullOrBlank()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        } else if (apiKey.isNullOrBlank()) {
            Log.e("MainPage", "Google Maps API key missing from manifest metadata")
        }

        setContent {
            DriverAssistTheme {
                MapScreen()
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mapsApiKey = remember(context) { resolveMapsApiKey(context) }
    val restroomTypes = listOf(
        "Public Restroom",
        "fast food restaurant restroom",
        "gas station restroom",
        "coffee shop restroom",
        "restaurant restroom",
        "bar restroom",
        "mall restroom"
    )
    var selectedTypeIndex by remember { mutableStateOf(0) }
    val selectedType = restroomTypes[selectedTypeIndex]
    var hasLocationPermission by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var bathroomLocations by remember { mutableStateOf<List<Place>>(emptyList()) }
    val cameraPositionState = rememberCameraPositionState()
    val placesClient = remember { Places.createClient(context) }
    var showSearchThisArea by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingRoute by remember { mutableStateOf(false) }
    val restroomReviews = remember { mutableStateMapOf<String, RestroomReview>() }
    var selectedPlaceName by remember { mutableStateOf<String?>(null) }
    var selectedReview by remember { mutableStateOf<RestroomReview?>(null) }
    var activeRoute by remember { mutableStateOf<RouteDetails?>(null) }
    var activeRouteDestinationName by remember { mutableStateOf<String?>(null) }
    var activeRouteDestinationLocation by remember { mutableStateOf<LatLng?>(null) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }

    fun searchForBathrooms(center: LatLng, query: String) {
        isSearching = true
        val placeFields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION)
        val searchRequest = SearchByTextRequest.builder(query, placeFields)
            .setLocationBias(CircularBounds.newInstance(center, 5000.0))
            .setMaxResultCount(20)
            .build()

        placesClient.searchByText(searchRequest)
            .addOnSuccessListener { response ->
                bathroomLocations = response.places
                activeRoute = null
                activeRouteDestinationName = null
                activeRouteDestinationLocation = null
                selectedPlace = null
                selectedReview = null
                selectedPlaceName = null
                isSearching = false
                showSearchThisArea = false
            }
            .addOnFailureListener { exception ->
                Log.e("BathroomSearch", "Search failed: ${exception.message}")
                isSearching = false
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    userLocation = latLng
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)

                    // Initial search near user
                    searchForBathrooms(latLng, selectedType)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) {
            showSearchThisArea = true
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = com.google.maps.android.compose.MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = hasLocationPermission,
                isTrafficEnabled = false,
                isIndoorEnabled = false,
                isBuildingEnabled = false,
            ),
            uiSettings = com.google.maps.android.compose.MapUiSettings(
                zoomControlsEnabled = true,
                compassEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = true
            ),
            contentPadding = PaddingValues(top = 80.dp, bottom = 96.dp, start = 16.dp, end = 16.dp)
        ) {
            activeRoute?.let { route ->
                Polyline(
                    points = route.points,
                    color = Color(0xFF1E88E5),
                    width = 14f
                )
            }

            bathroomLocations.forEach { place ->
                place.location?.let { latLng ->
                    Marker(
                        state = MarkerState(position = latLng),
                        title = place.displayName,
                        snippet = "Tap for review",
                        onClick = {
                            selectedPlace = place
                            val placeId = place.id ?: "${latLng.latitude},${latLng.longitude}"
                            selectedPlaceName = place.displayName ?: "Restroom"
                            selectedReview = restroomReviews.getOrPut(placeId) {
                                buildLocalReview(place)
                            }
                            true
                        }
                    )
                }
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp),
            color = Color.White.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                restroomTypes.forEachIndexed { index, type ->
                    AssistChip(
                        onClick = {
                            selectedTypeIndex = index
                            searchForBathrooms(cameraPositionState.position.target, type)
                        },
                        label = { Text(text = type) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        enabled = !isSearching
                    )
                }
            }
        }

        if (showSearchThisArea) {
            Button(
                onClick = {
                    val center = cameraPositionState.position.target
                    searchForBathrooms(center, selectedType)
                },
                enabled = !isSearching,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp) // Pushed up to avoid overlapping status bar/top UI
            ) {
                Text(if (isSearching) "Searching..." else "Search this area")
            }
        }

        if (bathroomLocations.isNotEmpty()) {
            Button(
                onClick = {
                    val origin = userLocation
                    if (origin == null) {
                        Toast.makeText(context, "Current location unavailable", Toast.LENGTH_SHORT)
                            .show()
                        return@Button
                    }

                    val nearest = findNearestBathroom(origin, bathroomLocations)
                    val destination = nearest?.location

                    if (destination == null) {
                        Toast.makeText(context, "No restroom location available", Toast.LENGTH_SHORT)
                            .show()
                        return@Button
                    }

                    if (mapsApiKey.isNullOrBlank()) {
                        Toast.makeText(context, "Maps API key unavailable", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoadingRoute = true
                    coroutineScope.launch {
                        val route = fetchWalkingRoute(mapsApiKey, origin, destination)
                        isLoadingRoute = false

                        if (route == null) {
                            Toast.makeText(context, "Unable to load route right now", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            activeRoute = route
                            activeRouteDestinationName = nearest.displayName ?: "Nearest restroom"
                            activeRouteDestinationLocation = destination
                        }
                    }
                },
                enabled = !isSearching && !isLoadingRoute,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            ) {
                Text(if (isLoadingRoute) "Loading route..." else "Show route to nearest restroom")
            }
        }

        activeRoute?.let { route ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 136.dp),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Route to ${activeRouteDestinationName ?: "nearest restroom"}: ${route.distanceText} • ${route.durationText}",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            activeRouteDestinationLocation?.let { location ->
                                val intentUri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}")
                                val mapIntent = Intent(Intent.ACTION_VIEW, intentUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                context.startActivity(mapIntent)
                            }
                        }
                    ) {
                        Text("Navigate")
                    }
                    TextButton(
                        onClick = {
                            activeRoute = null
                            activeRouteDestinationName = null
                            activeRouteDestinationLocation = null
                        }
                    ) {
                        Text("Clear")
                    }
                }
            }
        }

        if (selectedReview != null && selectedPlaceName != null) {
            AlertDialog(
                onDismissRequest = {
                    selectedReview = null
                    selectedPlaceName = null
                    selectedPlace = null
                },
                title = { Text(selectedPlaceName ?: "Restroom") },
                text = {
                    val review = selectedReview
                    if (review != null) {
                        Text(
                            "Cleanliness: ${review.cleanlinessRating}/5\n" +
                                "Code required: ${codeRequiredLabel(review.codeRequired)}\n" +
                                "Hours: ${review.hoursText}\n\n" +
                                "Review: ${review.reviewText}"
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedPlace?.let { place ->
                                place.location?.let { location ->
                                    val intentUri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, intentUri)
                                    mapIntent.setPackage("com.google.android.apps.maps")
                                    context.startActivity(mapIntent)
                                }
                            }
                            selectedReview = null
                            selectedPlaceName = null
                            selectedPlace = null
                        }
                    ) {
                        Text("Navigate")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            selectedReview = null
                            selectedPlaceName = null
                            selectedPlace = null
                        }
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
