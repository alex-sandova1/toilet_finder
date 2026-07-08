package com.example.driverassist.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.driverassist.model.RestroomAggregate
import com.example.driverassist.model.dirtyLikelihoodPercent
import com.example.driverassist.model.isClosedNow
import com.example.driverassist.model.isDirtyNow
import com.example.driverassist.util.resolveMapsApiKey
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mapsApiKey = remember(context) { resolveMapsApiKey(context) }
    val cameraPositionState = rememberCameraPositionState()
    val placesClient = remember { Places.createClient(context) }

    // Observe toast messages from ViewModel
    LaunchedEffect(viewModel.toastMessage) {
        viewModel.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    // Handles location permission requests.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        viewModel.hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                                         permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (viewModel.hasLocationPermission) {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    viewModel.userLocation = latLng
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                    viewModel.searchForBathrooms(placesClient, latLng, viewModel.selectedType)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) viewModel.onCameraMoved()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = viewModel.hasLocationPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false),
            contentPadding = PaddingValues(top = 80.dp, bottom = 96.dp, start = 16.dp, end = 16.dp),
            onMapLongClick = { viewModel.onMapLongClick(it) }
        ) {
            viewModel.activeRoute?.let { route ->
                Polyline(points = route.points, color = Color(0xFF1E88E5), width = 14f)
            }

            // Google Places Markers
            viewModel.visibleGoogleRestrooms.forEach { place ->
                place.location?.let { latLng ->
                    Marker(
                        state = MarkerState(position = latLng),
                        title = place.displayName,
                        snippet = "Tap for community status",
                        onClick = {
                            viewModel.loadFeedbackForPlace(place)
                            true
                        }
                    )
                }
            }

            // Custom Community Markers
            viewModel.visibleCustomRestrooms.forEach { custom ->
                Marker(
                    state = MarkerState(position = LatLng(custom.latitude, custom.longitude)),
                    title = custom.name,
                    snippet = "Community Added",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    onClick = {
                        viewModel.loadFeedbackForCustom(custom)
                        true
                    }
                )
            }
            
            // Temporary marker for the one being added
            viewModel.pendingNewRestroomLocation?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "New Restroom Location",
                    alpha = 0.7f
                )
            }
        }

        // Selection chips for restroom types.
        Surface(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), color = Color.White.copy(alpha = 0.95f), shadowElevation = 8.dp) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                viewModel.restroomTypes.forEachIndexed { index, type ->
                    AssistChip(
                        onClick = { viewModel.updateSelectedType(index, placesClient, cameraPositionState.position.target) },
                        label = { Text(type) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        enabled = !viewModel.isSearching
                    )
                }
            }
        }

        if (viewModel.showSearchThisArea) {
            Button(
                onClick = { viewModel.searchForBathrooms(placesClient, cameraPositionState.position.target, viewModel.selectedType) },
                enabled = !viewModel.isSearching,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            ) {
                Text(if (viewModel.isSearching) "Searching..." else "Search this area")
            }
        }

        if (viewModel.bathroomLocations.isNotEmpty()) {
            Button(
                onClick = {
                    val origin = viewModel.userLocation ?: return@Button
                    val key = mapsApiKey ?: return@Button
                    viewModel.fetchRoute(origin, key)
                },
                enabled = !viewModel.isSearching && !viewModel.isLoadingRoute,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            ) {
                Text(if (viewModel.isLoadingRoute) "Loading route..." else "Show route to nearest restroom")
            }
        }

        viewModel.activeRoute?.let { route ->
            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 136.dp), color = Color.White.copy(alpha = 0.95f), shadowElevation = 8.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Route to ${viewModel.activeRouteDestinationName ?: "nearest"}: ${route.distanceText} • ${route.durationText}", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        viewModel.activeRouteDestinationLocation?.let { loc ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}")).apply { setPackage("com.google.android.apps.maps") }
                            context.startActivity(intent)
                        }
                    }) { Text("Navigate") }
                    TextButton(onClick = { viewModel.clearRoute() }) { Text("Clear") }
                }
            }
        }

        if (viewModel.selectedRestroomId != null && viewModel.selectedRestroomName != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearSelectedPlace() },
                title = { Text(viewModel.selectedRestroomName ?: "Restroom") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (viewModel.isLoadingFeedback) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Loading community status…")
                            }
                        } else {
                            viewModel.selectedAggregate?.let { agg ->
                                RestroomAggregateSummary(aggregate = agg)
                                if (agg.note.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Note: ${agg.note}",
                                            modifier = Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            } ?: Text("No community reports yet. You can add the first rating or status update.")

                            viewModel.feedbackErrorMessage?.let {
                                Text(text = it, color = MaterialTheme.colorScheme.error)
                            }

                            Text(
                                text = "Update status or add a note:",
                                style = MaterialTheme.typography.titleSmall
                            )

                            OutlinedTextField(
                                value = viewModel.userNoteUpdate,
                                onValueChange = { viewModel.userNoteUpdate = it },
                                label = { Text("Add/Update Note") },
                                placeholder = { Text("e.g. Back of the restaurant") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Change Category", style = MaterialTheme.typography.labelLarge)
                            var isDropdownExpanded by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = isDropdownExpanded,
                                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = viewModel.userCategoryUpdate,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = isDropdownExpanded,
                                    onDismissRequest = { isDropdownExpanded = false }
                                ) {
                                    viewModel.restroomTypes.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type) },
                                            onClick = {
                                                viewModel.userCategoryUpdate = type
                                                isDropdownExpanded = false
                                            },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Rate how likely this restroom is to be dirty over time. 1 means very likely dirty, 5 means usually clean.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            CleanlinessRatingRow(
                                selectedRating = viewModel.selectedCleanlinessRating,
                                onRatingSelected = { viewModel.selectedCleanlinessRating = it }
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { viewModel.submitFeedback(includeRating = true) },
                                    enabled = !viewModel.isSubmittingFeedback
                                ) {
                                    Text(if (viewModel.userNoteUpdate.isNotBlank()) "Save Update" else "Save Rating")
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.submitFeedback(markedDirty = true) },
                                    enabled = !viewModel.isSubmittingFeedback
                                ) {
                                    Text("Dirty now")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.submitFeedback(markedClosed = true) },
                                    enabled = !viewModel.isSubmittingFeedback
                                ) {
                                    Text("Closed now")
                                }
                            }

                            if (viewModel.isSubmittingFeedback) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Saving update…")
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 8.dp))

                            TextButton(
                                onClick = { viewModel.deleteRestroom() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.align(Alignment.End),
                                enabled = !viewModel.isSubmittingFeedback
                            ) {
                                val label = if (viewModel.selectedPlace == null) "Delete Restroom" else "Report as Incorrect"
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.selectedRestroomLocation?.let { loc ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}")).apply { setPackage("com.google.android.apps.maps") }
                            context.startActivity(intent)
                        }
                    }) { Text("Navigate") }
                },
                dismissButton = { TextButton(onClick = { viewModel.clearSelectedPlace() }) { Text("Close") } }
            )
        }

        // Add Restroom Dialog
        if (viewModel.pendingNewRestroomLocation != null) {
            AlertDialog(
                onDismissRequest = { viewModel.pendingNewRestroomLocation = null },
                title = { Text("Add Missing Restroom") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text("Give this location a name and category so others can find it.")
                        OutlinedTextField(
                            value = viewModel.newRestroomName,
                            onValueChange = { viewModel.newRestroomName = it },
                            label = { Text("Restroom Name") },
                            placeholder = { Text("e.g. Central Park North") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = viewModel.newRestroomNote,
                            onValueChange = { viewModel.newRestroomNote = it },
                            label = { Text("Notes (Optional)") },
                            placeholder = { Text("e.g. Inside Hector's Mariscos") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Text("Category", style = MaterialTheme.typography.labelLarge)
                        var isAddDropdownExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = isAddDropdownExpanded,
                            onExpandedChange = { isAddDropdownExpanded = !isAddDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = viewModel.newRestroomCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAddDropdownExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = isAddDropdownExpanded,
                                onDismissRequest = { isAddDropdownExpanded = false }
                            ) {
                                viewModel.restroomTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            viewModel.newRestroomCategory = type
                                            isAddDropdownExpanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.saveCustomRestroom() },
                        enabled = viewModel.newRestroomName.isNotBlank()
                    ) {
                        Text("Add to Map")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.pendingNewRestroomLocation = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Custom My Location button positioned near zoom controls.
        FloatingActionButton(
            onClick = {
                viewModel.userLocation?.let {
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
