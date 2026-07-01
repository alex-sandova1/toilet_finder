package com.example.driverassist

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.maps.android.compose.rememberCameraPositionState

class MainPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the New Places SDK
        if (!Places.isInitialized()) {
            // Note: In a real app, you'd fetch this from a secure place or BuildConfig
            val apiKey = "AIzaSyCU3SG7RETjnihdh7UeZmu7DndbE95PIwI"
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
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
    var hasLocationPermission by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var bathroomLocations by remember { mutableStateOf<List<Place>>(emptyList()) }
    val cameraPositionState = rememberCameraPositionState()
    val placesClient = remember { Places.createClient(context) }

    var showSearchThisArea by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    fun searchForBathrooms(center: LatLng) {
        isSearching = true
        val placeFields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION)
        val searchRequest = SearchByTextRequest.builder("public restroom", placeFields)
            .setLocationBias(CircularBounds.newInstance(center, 5000.0))
            .setMaxResultCount(20)
            .build()

        placesClient.searchByText(searchRequest)
            .addOnSuccessListener { response ->
                bathroomLocations = response.places
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
                    searchForBathrooms(latLng)
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
            contentPadding = PaddingValues(16.dp)
        ) {
            bathroomLocations.forEach { place ->
                place.location?.let { latLng ->
                    Marker(
                        state = MarkerState(position = latLng),
                        title = place.displayName,
                        snippet = "Bathroom"
                    )
                }
            }
        }

        if (showSearchThisArea) {
            Button(
                onClick = {
                    val center = cameraPositionState.position.target
                    searchForBathrooms(center)
                },
                enabled = !isSearching,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp) // Pushed down to avoid overlapping status bar/top UI
            ) {
                Text(if (isSearching) "Searching..." else "Search this area")
            }
        }
    }
}
