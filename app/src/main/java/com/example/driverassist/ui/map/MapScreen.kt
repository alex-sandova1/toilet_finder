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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.driverassist.model.*
import com.example.driverassist.network.fetchWalkingRoute
import com.example.driverassist.util.*
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

// Main map interface composable.
@SuppressLint("MissingPermission")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mapsApiKey = remember(context) { resolveMapsApiKey(context) }
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
    val restroomReviews = remember { mutableStateMapOf<String, RestroomReview>() }
    
    var selectedPlaceName by remember { mutableStateOf<String?>(null) }
    var selectedReview by remember { mutableStateOf<RestroomReview?>(null) }
    var activeRoute by remember { mutableStateOf<RouteDetails?>(null) }
    var activeRouteDestinationName by remember { mutableStateOf<String?>(null) }
    var activeRouteDestinationLocation by remember { mutableStateOf<LatLng?>(null) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }

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
                selectedPlace = null
                selectedReview = null
                selectedPlaceName = null
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
                        snippet = "Tap for review",
                        onClick = {
                            selectedPlace = place
                            val id = place.id ?: "${latLng.latitude},${latLng.longitude}"
                            selectedPlaceName = place.displayName ?: "Restroom"
                            selectedReview = restroomReviews.getOrPut(id) { buildLocalReview(place) }
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

        if (selectedReview != null && selectedPlaceName != null) {
            AlertDialog(
                onDismissRequest = { selectedReview = null; selectedPlaceName = null; selectedPlace = null },
                title = { Text(selectedPlaceName ?: "Restroom") },
                text = { selectedReview?.let { Text("Cleanliness: ${it.cleanlinessRating}/5\nCode: ${codeRequiredLabel(it.codeRequired)}\nHours: ${it.hoursText}\n\nReview: ${it.reviewText}") } },
                confirmButton = {
                    TextButton(onClick = {
                        selectedPlace?.location?.let { loc ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}")).apply { setPackage("com.google.android.apps.maps") }
                            context.startActivity(intent)
                        }
                        selectedReview = null; selectedPlaceName = null; selectedPlace = null
                    }) { Text("Navigate") }
                },
                dismissButton = { TextButton(onClick = { selectedReview = null; selectedPlaceName = null; selectedPlace = null }) { Text("Close") } }
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
