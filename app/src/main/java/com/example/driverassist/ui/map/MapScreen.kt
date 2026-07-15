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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.driverassist.model.CustomRestroom
import com.example.driverassist.model.RestroomAggregate
import com.example.driverassist.model.dirtyLikelihoodPercent
import com.example.driverassist.model.isClosedNow
import com.example.driverassist.model.isDirtyNow
import com.example.driverassist.model.isRecentlyVerified
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
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
    val cameraPositionState = rememberCameraPositionState()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val placesClient = remember { Places.createClient(context) }
    val mapProperties by remember(viewModel.hasLocationPermission) {
        mutableStateOf(MapProperties(isMyLocationEnabled = viewModel.hasLocationPermission))
    }
    val mapUiSettings by remember {
        mutableStateOf(MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false))
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

    DisposableEffect(viewModel.hasLocationPermission) {
        var callback: LocationCallback? = null
        if (viewModel.hasLocationPermission) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build()

            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        viewModel.userLocation = LatLng(it.latitude, it.longitude)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                android.os.Looper.getMainLooper()
            )
        }
        onDispose {
            callback?.let { fusedLocationClient.removeLocationUpdates(it) }
        }
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) viewModel.onCameraMoved()
    }

    var showAccountDialog by remember { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 60.dp,
        sheetContent = {
            RestroomListView(
                viewModel = viewModel,
                onRestroomClick = { latLng, place, custom ->
                    coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                        if (place != null) viewModel.loadFeedbackForPlace(place)
                        else if (custom != null) viewModel.loadFeedbackForCustom(custom)
                        sheetState.partialExpand()
                    }
                }
            )
        },
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

            if (viewModel.userProfile?.isVerifiedUser == true && viewModel.searchHistory.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                Text(
                                    "Recents:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            items(viewModel.searchHistory) { query ->
                                AssistChip(
                                    onClick = {
                                        viewModel.searchForBathrooms(
                                            placesClient,
                                            cameraPositionState.position.target,
                                            query
                                        )
                                    },
                                    label = { Text(query, style = MaterialTheme.typography.bodySmall) },
                                    leadingIcon = { Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp)) },
                                    border = null,
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = mapUiSettings,
                contentPadding = PaddingValues(bottom = 64.dp, start = 16.dp, end = 16.dp),
                onMapLongClick = { viewModel.onMapLongClick(it) }
            ) {
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
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (viewModel.visibleGoogleRestrooms.isNotEmpty() || viewModel.visibleCustomRestrooms.isNotEmpty()) {
                Button(
                    onClick = {
                        val nearest = viewModel.findAndNavigateToNearestRestroom(context)
                        if (nearest == null) {
                            Toast.makeText(context, "No restrooms found nearby. Check GPS.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !viewModel.isSearching,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
                ) {
                    Text("Navigate to nearest restroom")
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
                                    
                                    if (agg.needsPasscode || agg.isTruckFriendly) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (agg.needsPasscode) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = MaterialTheme.shapes.small,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.VpnKey, null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("Code Required", style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                            if (agg.isTruckFriendly) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = MaterialTheme.shapes.small,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.LocalShipping, null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("Truck Friendly", style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (agg.note.isNotBlank()) {
                                        Text(
                                            text = "Note: ${agg.note}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                } ?: Text("No community reports yet.")

                                viewModel.feedbackErrorMessage?.let {
                                    Text(text = it, color = MaterialTheme.colorScheme.error)
                                }

                                if (!showFeedbackInputs) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.confirmStillClean() },
                                            modifier = Modifier.weight(1f),
                                            enabled = !viewModel.isSubmittingFeedback,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                        ) {
                                            Icon(Icons.Default.ThumbUp, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Still Clean")
                                        }

                                        Button(
                                            onClick = { showFeedbackInputs = true },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                        ) {
                                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Update")
                                        }
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = viewModel.userNoteUpdate,
                                        onValueChange = { viewModel.userNoteUpdate = it },
                                        label = { Text("Note") },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = viewModel.needsPasscodeUpdate, onCheckedChange = { viewModel.needsPasscodeUpdate = it })
                                            Text("Passcode", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = viewModel.isTruckFriendlyUpdate, onCheckedChange = { viewModel.isTruckFriendlyUpdate = it })
                                            Text("Truck Friendly", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }

                                    CleanlinessRatingRow(
                                        selectedRating = viewModel.selectedCleanlinessRating,
                                        onRatingSelected = { viewModel.selectedCleanlinessRating = it }
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(selected = viewModel.markedDirtyUpdate, onClick = { viewModel.markedDirtyUpdate = !viewModel.markedDirtyUpdate }, label = { Text("Dirty Now") })
                                        FilterChip(selected = viewModel.markedClosedUpdate, onClick = { viewModel.markedClosedUpdate = !viewModel.markedClosedUpdate }, label = { Text("Closed Now") })
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { viewModel.submitFeedback(true); showFeedbackInputs = false }, modifier = Modifier.weight(1f)) {
                                            Text("Save")
                                        }
                                        TextButton(onClick = { showFeedbackInputs = false }) { Text("Cancel") }
                                    }
                                }

                                if (viewModel.isSubmittingFeedback) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }

                                Divider(modifier = Modifier.padding(vertical = 4.dp))

                                Button(
                                    onClick = { viewModel.flagAsIncorrect() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                ) {
                                    Icon(Icons.Default.LocationOff, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Not a Restroom")
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

                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = viewModel.newRestroomNeedsPasscode,
                                        onCheckedChange = { viewModel.newRestroomNeedsPasscode = it }
                                    )
                                    Text("Passcode", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = viewModel.newRestroomIsTruckFriendly,
                                        onCheckedChange = { viewModel.newRestroomIsTruckFriendly = it }
                                    )
                                    Text("Truck Friendly", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            
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

            // Custom Zoom Controls on the left
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.zoomIn())
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.zoomOut())
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }
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
                    .padding(end = 12.dp, bottom = 140.dp),
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
fun RestroomListView(
    viewModel: MapViewModel,
    onRestroomClick: (LatLng, com.google.android.libraries.places.api.model.Place?, CustomRestroom?) -> Unit
) {
    val googleRestrooms = viewModel.visibleGoogleRestrooms
    val customRestrooms = viewModel.visibleCustomRestrooms
    val userLocation = viewModel.userLocation

    // Combine and sort by distance if location is available, then limit to 10
    val combinedList = remember(googleRestrooms, customRestrooms, userLocation) {
        val list = mutableListOf<RestroomListItemData>()
        
        googleRestrooms.forEach { place ->
            place.location?.let { loc ->
                list.add(RestroomListItemData(
                    id = place.id ?: "",
                    name = place.displayName ?: "Restroom",
                    location = loc,
                    category = viewModel.selectedType,
                    googlePlace = place
                ))
            }
        }
        
        customRestrooms.forEach { custom ->
            list.add(RestroomListItemData(
                id = custom.id,
                name = custom.name,
                location = LatLng(custom.latitude, custom.longitude),
                category = custom.category,
                customRestroom = custom
            ))
        }

        if (userLocation != null) {
            list.sortedBy { com.example.driverassist.util.distanceMeters(userLocation, it.location) }
                .take(10)
        } else {
            list.take(10)
        }
    }

    Column(modifier = Modifier.fillMaxHeight(0.8f).padding(horizontal = 16.dp)) {
        Text(
            text = "Nearby Restrooms",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        if (combinedList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No restrooms found in this area.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(combinedList) { item ->
                    RestroomRow(
                        item = item,
                        aggregate = viewModel.restroomAggregates[item.id],
                        userLocation = userLocation,
                        onClick = { onRestroomClick(item.location, item.googlePlace, item.customRestroom) }
                    )
                }
            }
        }
    }
}

data class RestroomListItemData(
    val id: String,
    val name: String,
    val location: LatLng,
    val category: String,
    val googlePlace: com.google.android.libraries.places.api.model.Place? = null,
    val customRestroom: CustomRestroom? = null
)

@Composable
fun RestroomRow(
    item: RestroomListItemData,
    aggregate: com.example.driverassist.model.RestroomAggregate?,
    userLocation: LatLng?,
    onClick: () -> Unit
) {
    val distance = if (userLocation != null) {
        com.example.driverassist.util.distanceMeters(userLocation, item.location)
    } else null

    val now = System.currentTimeMillis()
    val isDirty = aggregate?.isDirtyNow(now) == true
    val isClosed = aggregate?.isClosedNow(now) == true
    val isRecentlyVerified = aggregate?.isRecentlyVerified(now) == true

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon based on category
            val icon = when {
                item.category.contains("coffee", ignoreCase = true) -> Icons.Default.Coffee
                item.category.contains("gas", ignoreCase = true) -> Icons.Default.LocalGasStation
                item.category.contains("food", ignoreCase = true) || item.category.contains("restaurant", ignoreCase = true) -> Icons.Default.Restaurant
                item.category.contains("mall", ignoreCase = true) -> Icons.Default.Store
                else -> Icons.Default.Wc
            }
            
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    modifier = Modifier.size(20.dp), 
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name, 
                    style = MaterialTheme.typography.titleSmall, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (distance != null) {
                        Text(
                            "${String.format(Locale.US, "%.1f", distance / 1000.0)} km", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(" • ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        item.category, 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                if (isDirty || isClosed || isRecentlyVerified) {
                    Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isRecentlyVerified) Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) { Text("Verified", style = MaterialTheme.typography.labelSmall) }
                        if (isDirty) Badge(containerColor = MaterialTheme.colorScheme.errorContainer) { Text("Dirty", style = MaterialTheme.typography.labelSmall) }
                        if (isClosed) Badge(containerColor = MaterialTheme.colorScheme.error) { Text("Closed", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (aggregate?.needsPasscode == true) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Passcode",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        if (aggregate != null && aggregate.ratingCount > 0) {
                            Text(
                                text = String.format(Locale.US, "%.1f", aggregate.avgCleanliness),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (aggregate.avgCleanliness >= 3.5) Color(0xFF2E7D32) else if (aggregate.avgCleanliness >= 2.5) Color(0xFFF57C00) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (aggregate != null && aggregate.ratingCount > 0) {
                        Text("Rating", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.width(8.dp))

                val context = LocalContext.current
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${item.location.latitude},${item.location.longitude}")).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Navigation, 
                        contentDescription = "Navigate", 
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
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
            if (aggregate.isRecentlyVerified(nowMillis)) add("Recently verified")
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
