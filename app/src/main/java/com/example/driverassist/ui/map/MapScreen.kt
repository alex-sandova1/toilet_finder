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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Edit
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
    val mapProperties by remember(viewModel.hasLocationPermission) {
        mutableStateOf(MapProperties(isMyLocationEnabled = viewModel.hasLocationPermission))
    }
    val mapUiSettings by remember {
        mutableStateOf(MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false))
    }

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
                } ?: run {
                    // Fallback if last location is unavailable (e.g. on some emulators)
                    viewModel.searchForBathrooms(placesClient, LatLng(37.4220, -122.0841), viewModel.selectedType)
                }
            }.addOnFailureListener {
                viewModel.searchForBathrooms(placesClient, LatLng(37.4220, -122.0841), viewModel.selectedType)
            }
        } else {
            // If permission denied, stop loading
            viewModel.searchForBathrooms(placesClient, LatLng(37.4220, -122.0841), viewModel.selectedType)
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) viewModel.onCameraMoved()
    }

    var showAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(viewModel.restroomTypes) { index, type ->
                            val selected = viewModel.selectedTypeIndex == index
                            FilterChip(
                                selected = selected,
                                onClick = { 
                                    viewModel.updateSelectedType(index, placesClient, cameraPositionState.position.target) 
                                },
                                label = { Text(type) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null,
                                enabled = !viewModel.isSearching
                            )
                        }
                    }
                },
                actions = {
                    val isVerified = viewModel.userProfile?.isVerifiedUser == true
                    IconButton(
                        onClick = { viewModel.toggleVerifiedFilter() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Clean Filter",
                            tint = if (viewModel.isVerifiedFilterEnabled) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                if (isVerified) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            }
                        )
                    }

                    IconButton(onClick = { showAccountDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Account",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = mapUiSettings,
                contentPadding = PaddingValues(bottom = 96.dp, start = 16.dp, end = 16.dp),
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

            if (viewModel.showSearchThisArea) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.searchForBathrooms(placesClient, cameraPositionState.position.target, viewModel.selectedType) },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    text = { Text(if (viewModel.isSearching) "Searching..." else "Search this area") },
                    expanded = !viewModel.isSearching,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
                var showFeedbackInputs by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { 
                        viewModel.clearSelectedPlace()
                        showFeedbackInputs = false
                    },
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

                                if (!showFeedbackInputs) {
                                    Button(
                                        onClick = { showFeedbackInputs = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Rate or Update Status")
                                    }
                                } else {
                                    Divider(modifier = Modifier.padding(vertical = 4.dp))
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
                                    FilterChip(
                                        selected = viewModel.markedDirtyUpdate,
                                        onClick = { viewModel.markedDirtyUpdate = !viewModel.markedDirtyUpdate },
                                        label = { Text("Dirty now") },
                                        enabled = !viewModel.isSubmittingFeedback
                                    )
                                    FilterChip(
                                        selected = viewModel.markedClosedUpdate,
                                        onClick = { viewModel.markedClosedUpdate = !viewModel.markedClosedUpdate },
                                        label = { Text("Closed now") },
                                        enabled = !viewModel.isSubmittingFeedback
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { 
                                            viewModel.submitFeedback(includeRating = true)
                                            showFeedbackInputs = false
                                        },
                                        enabled = !viewModel.isSubmittingFeedback
                                    ) {
                                        val label = if (viewModel.userNoteUpdate.isNotBlank() || viewModel.markedDirtyUpdate || viewModel.markedClosedUpdate) {
                                            "Save Update"
                                        } else {
                                            "Save Rating"
                                        }
                                        Text(label)
                                    }
                                    
                                    TextButton(onClick = { showFeedbackInputs = false }) {
                                        Text("Cancel")
                                    }
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
                    dismissButton = { 
                        TextButton(onClick = { 
                            viewModel.clearSelectedPlace()
                            showFeedbackInputs = false
                        }) { Text("Close") } 
                    }
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
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location")
            }

            if (showAccountDialog) {
                AlertDialog(
                    onDismissRequest = { showAccountDialog = false },
                    title = { Text("My Account") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            viewModel.currentUser?.let { user ->
                                Text("Signed in as:", style = MaterialTheme.typography.labelLarge)
                                Text(user.displayName ?: "User", style = MaterialTheme.typography.bodyLarge)
                                Text(user.email ?: "", style = MaterialTheme.typography.bodySmall)
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Subscription Status
                                Surface(
                                    color = if (viewModel.userProfile?.isVerifiedUser == true) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        val statusText = if (viewModel.userProfile?.isVerifiedUser == true) "Status: Verified User" else "Status: Free User"
                                        Text(statusText, style = MaterialTheme.typography.labelMedium)
                                        if (viewModel.userProfile?.isVerifiedUser != true) {
                                            Text("Upgrade for $2/mo to get Verified Clean filters.", style = MaterialTheme.typography.bodySmall)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    // MOCK: In a real app, this would trigger a payment flow
                                                    // For now, we can show a Toast or just mock the state if we had a method
                                                    Toast.makeText(context, "Payment flow would start here!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                            ) {
                                                Text("Upgrade Now")
                                            }
                                        } else {
                                            Text("Premium features unlocked.", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            } ?: Text("You are not signed in.")
                        }
                    },
                    confirmButton = {
                    TextButton(onClick = { showAccountDialog = false }) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    if (viewModel.currentUser != null) {
                        TextButton(
                            onClick = {
                                viewModel.signOut()
                                showAccountDialog = false
                                // Navigate to login
                                val intent = Intent(context, com.example.driverassist.login.LoginActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Sign Out")
                        }
                    } else {
                        Button(
                            onClick = {
                                showAccountDialog = false
                                val intent = Intent(context, com.example.driverassist.login.LoginActivity::class.java)
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Sign In")
                        }
                    }
                }
            )
            }
        }

        if (viewModel.isInitialLoading) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Finding nearby restrooms...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
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
