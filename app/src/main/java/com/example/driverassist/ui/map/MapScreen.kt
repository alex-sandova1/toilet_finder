package com.example.driverassist.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.driverassist.data.RestroomFeedbackRepository
import com.example.driverassist.model.RestroomAggregate
import com.example.driverassist.model.RestroomReportInput
import com.example.driverassist.model.RouteDetails
import com.example.driverassist.model.dirtyLikelihoodPercent
import com.example.driverassist.model.isClosedNow
import com.example.driverassist.model.isDirtyNow
import com.example.driverassist.network.fetchWalkingRoute
import com.example.driverassist.util.findNearestBathroom
import com.example.driverassist.util.resolveMapsApiKey
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.util.Locale

// Main map interface composable.
@SuppressLint("MissingPermission")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mapsApiKey = remember(context) { resolveMapsApiKey(context) }
    val feedbackRepository = remember { RestroomFeedbackRepository() }
    val restroomTypes = listOf("Public Restroom", "fast food restaurant restroom", "gas station restroom", "coffee shop restroom", "restaurant restroom", "bar restroom", "mall restroom")

    var selectedTypeIndex by remember { mutableIntStateOf(0) }
    val selectedType = restroomTypes[selectedTypeIndex]
    var hasLocationPermission by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var bathroomLocations by remember { mutableStateOf<List<Place>>(emptyList()) }
    val cameraPositionState = rememberCameraPositionState()
    val placesClient = remember { Places.createClient(context) }

    var showSearchThisArea by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingRoute by remember { mutableStateOf(false) }

    var selectedPlaceName by remember { mutableStateOf<String?>(null) }
    var selectedAggregate by remember { mutableStateOf<RestroomAggregate?>(null) }
    var isLoadingFeedback by remember { mutableStateOf(false) }
    var isSubmittingFeedback by remember { mutableStateOf(false) }
    var feedbackErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedCleanlinessRating by remember { mutableIntStateOf(3) }
    var activeRoute by remember { mutableStateOf<RouteDetails?>(null) }
    var activeRouteDestinationName by remember { mutableStateOf<String?>(null) }
    var activeRouteDestinationLocation by remember { mutableStateOf<LatLng?>(null) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }

    fun clearSelectedPlace() {
        selectedPlace = null
        selectedPlaceName = null
        selectedAggregate = null
        isLoadingFeedback = false
        isSubmittingFeedback = false
        feedbackErrorMessage = null
        selectedCleanlinessRating = 3
    }

    fun loadFeedbackForPlace(place: Place) {
        selectedPlace = place
        selectedPlaceName = place.displayName ?: "Restroom"
        selectedAggregate = null
        feedbackErrorMessage = null
        selectedCleanlinessRating = 3
        isLoadingFeedback = true

        coroutineScope.launch {
            runCatching {
                feedbackRepository.fetchAggregate(place)
            }.onSuccess { aggregate ->
                selectedAggregate = aggregate
            }.onFailure { error ->
                feedbackErrorMessage = error.message ?: "Unable to load community status right now."
            }
            isLoadingFeedback = false
        }
    }

    fun submitFeedback(markedDirty: Boolean = false, markedClosed: Boolean = false, includeRating: Boolean = false) {
        val place = selectedPlace ?: return
        isSubmittingFeedback = true
        feedbackErrorMessage = null

        coroutineScope.launch {
            runCatching {
                feedbackRepository.submitReport(
                    place = place,
                    report = RestroomReportInput(
                        cleanlinessRating = selectedCleanlinessRating.takeIf { includeRating },
                        markedDirty = markedDirty,
                        markedClosed = markedClosed
                    )
                )
            }.onSuccess { updated ->
                selectedAggregate = updated
                val successMessage = when {
                    markedClosed -> "Marked restroom as temporarily closed."
                    markedDirty -> "Marked restroom as temporarily dirty."
                    includeRating -> "Saved cleanliness rating."
                    else -> "Update saved."
                }
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                feedbackErrorMessage = error.message ?: "Unable to save your update right now."
            }
            isSubmittingFeedback = false
        }
    }

    // Logic for searching nearby restrooms.
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
                clearSelectedPlace()
                isSearching = false
                showSearchThisArea = false
            }
            .addOnFailureListener { exception ->
                Log.e("Search", "Failed: ${exception.message}")
                isSearching = false
            }
    }

    // Handles location permission requests.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    userLocation = latLng
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                    searchForBathrooms(latLng, selectedType)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) showSearchThisArea = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false),
            contentPadding = PaddingValues(top = 80.dp, bottom = 96.dp, start = 16.dp, end = 16.dp)
        ) {
            activeRoute?.let { route ->
                Polyline(points = route.points, color = Color(0xFF1E88E5), width = 14f)
            }
            bathroomLocations.forEach { place ->
                place.location?.let { latLng ->
                    Marker(
                        state = MarkerState(position = latLng),
                        title = place.displayName,
                        snippet = "Tap for community status",
                        onClick = {
                            loadFeedbackForPlace(place)
                            true
                        }
                    )
                }
            }
        }

        // Selection chips for restroom types.
        Surface(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), color = Color.White.copy(alpha = 0.95f), shadowElevation = 8.dp) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                restroomTypes.forEachIndexed { index, type ->
                    AssistChip(
                        onClick = { selectedTypeIndex = index; searchForBathrooms(cameraPositionState.position.target, type) },
                        label = { Text(type) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        enabled = !isSearching
                    )
                }
            }
        }

        if (showSearchThisArea) {
            Button(onClick = { searchForBathrooms(cameraPositionState.position.target, selectedType) }, enabled = !isSearching, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                Text(if (isSearching) "Searching..." else "Search this area")
            }
        }

        if (bathroomLocations.isNotEmpty()) {
            Button(
                onClick = {
                    val origin = userLocation ?: return@Button
                    val nearest = findNearestBathroom(origin, bathroomLocations) ?: return@Button
                    val dest = nearest.location ?: return@Button
                    val key = mapsApiKey ?: return@Button
                    isLoadingRoute = true
                    coroutineScope.launch {
                        val route = fetchWalkingRoute(key, origin, dest)
                        isLoadingRoute = false
                        if (route != null) {
                            activeRoute = route
                            activeRouteDestinationName = nearest.displayName ?: "Nearest restroom"
                            activeRouteDestinationLocation = dest
                        } else Toast.makeText(context, "Unable to load route", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isSearching && !isLoadingRoute,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            ) {
                Text(if (isLoadingRoute) "Loading route..." else "Show route to nearest restroom")
            }
        }

        activeRoute?.let { route ->
            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 136.dp), color = Color.White.copy(alpha = 0.95f), shadowElevation = 8.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Route to ${activeRouteDestinationName ?: "nearest"}: ${route.distanceText} • ${route.durationText}", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        activeRouteDestinationLocation?.let { loc ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}")).apply { setPackage("com.google.android.apps.maps") }
                            context.startActivity(intent)
                        }
                    }) { Text("Navigate") }
                    TextButton(onClick = { activeRoute = null; activeRouteDestinationName = null; activeRouteDestinationLocation = null }) { Text("Clear") }
                }
            }
        }

        if (selectedPlace != null && selectedPlaceName != null) {
            AlertDialog(
                onDismissRequest = ::clearSelectedPlace,
                title = { Text(selectedPlaceName ?: "Restroom") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isLoadingFeedback) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Loading community status…")
                            }
                        } else {
                            selectedAggregate?.let {
                                RestroomAggregateSummary(aggregate = it)
                            } ?: Text("No community reports yet. You can add the first rating or status update.")

                            feedbackErrorMessage?.let {
                                Text(text = it, color = MaterialTheme.colorScheme.error)
                            }

                            Text(
                                text = "Rate how likely this restroom is to be dirty over time. 1 means very likely dirty, 5 means usually clean.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            CleanlinessRatingRow(
                                selectedRating = selectedCleanlinessRating,
                                onRatingSelected = { selectedCleanlinessRating = it }
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { submitFeedback(includeRating = true) },
                                    enabled = !isSubmittingFeedback
                                ) {
                                    Text("Save rating")
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { submitFeedback(markedDirty = true) },
                                    enabled = !isSubmittingFeedback
                                ) {
                                    Text("Dirty now")
                                }
                                OutlinedButton(
                                    onClick = { submitFeedback(markedClosed = true) },
                                    enabled = !isSubmittingFeedback
                                ) {
                                    Text("Closed now")
                                }
                            }

                            if (isSubmittingFeedback) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Saving update…")
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedPlace?.location?.let { loc ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}")).apply { setPackage("com.google.android.apps.maps") }
                            context.startActivity(intent)
                        }
                    }) { Text("Navigate") }
                },
                dismissButton = { TextButton(onClick = ::clearSelectedPlace) { Text("Close") } }
            )
        }

        // Custom My Location button positioned near zoom controls.
        FloatingActionButton(
            onClick = {
                userLocation?.let {
                    coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 200.dp),
            containerColor = Color.White,
            contentColor = Color(0xFF1E88E5),
            shape = androidx.compose.foundation.shape.CircleShape
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "My Location")
        }
    }
}

@Composable
private fun RestroomAggregateSummary(aggregate: RestroomAggregate) {
    val nowMillis = System.currentTimeMillis()
    val statusChips = remember(aggregate, nowMillis) {
        buildList {
            if (aggregate.isClosedNow(nowMillis)) add("Closed now")
            if (aggregate.isDirtyNow(nowMillis)) add("Reported dirty")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (statusChips.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statusChips.forEach { label ->
                    AssistChip(onClick = {}, label = { Text(label) })
                }
            }
        } else {
            Text("No active dirty or closed alerts right now.")
        }

        if (aggregate.ratingCount > 0) {
            Text("Dirty likelihood: ${aggregate.dirtyLikelihoodPercent()}%")
            Text(
                text = "Average cleanliness tendency: ${String.format(Locale.US, "%.1f", aggregate.avgCleanliness)}/5 from ${aggregate.ratingCount} rating${if (aggregate.ratingCount == 1) "" else "s"}.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text("No historical cleanliness ratings yet.")
        }

        Text(
            text = "Historical dirty alerts: ${aggregate.dirtyReports} • Historical closed alerts: ${aggregate.closedReports}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Temporary status alerts expire automatically. Ratings reflect long-term likelihood, not the current moment.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CleanlinessRatingRow(selectedRating: Int, onRatingSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (1..5).forEach { rating ->
            FilterChip(
                selected = selectedRating == rating,
                onClick = { onRatingSelected(rating) },
                label = { Text(rating.toString()) }
            )
        }
    }
}

