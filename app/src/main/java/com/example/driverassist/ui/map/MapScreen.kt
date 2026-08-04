package com.example.driverassist.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.driverassist.model.CustomRestroom
import com.example.driverassist.model.RestroomAggregate
import com.example.driverassist.model.dirtyLikelihoodPercent
import com.example.driverassist.model.isClosedNow
import com.example.driverassist.model.isDirtyNow
import com.example.driverassist.model.isRecentlyVerified
import com.example.driverassist.ui.components.AppLogo
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
import com.google.android.libraries.places.api.model.Place
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
                    viewModel.searchForBathrooms(placesClient, LatLng(37.4220, -122.0841), viewModel.selectedType)
                }
            }.addOnFailureListener {
                viewModel.searchForBathrooms(placesClient, LatLng(37.4220, -122.0841), viewModel.selectedType)
            }
        } else {
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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showFilterSheet by remember { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Only enable drawer gestures if the drawer is already open (for swiping closed).
        // This makes map interaction perfectly smooth when the drawer is closed.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Header
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        AppLogo(modifier = Modifier.scale(0.8f))
                        Spacer(Modifier.height(16.dp))
                        viewModel.currentUser?.let { user ->
                            Text(user.displayName ?: "User", style = MaterialTheme.typography.titleLarge)
                            Text(user.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider()

                    // Filters Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Filters", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        
                        NavigationDrawerItem(
                            label = { Text("Verified Clean") },
                            selected = viewModel.isVerifiedFilterEnabled,
                            onClick = { viewModel.toggleVerifiedFilter() },
                            icon = { Icon(Icons.Default.Verified, null) },
                            badge = {
                                if (viewModel.userProfile?.isVerifiedUser != true) {
                                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text("Advanced Filters") },
                            selected = false,
                            onClick = {
                                if (viewModel.userProfile?.isVerifiedUser == true) {
                                    showFilterSheet = true
                                    coroutineScope.launch { drawerState.close() }
                                } else {
                                    viewModel.toggleVerifiedFilter()
                                }
                            },
                            icon = { Icon(Icons.Default.FilterList, null) },
                            badge = {
                                if (viewModel.userProfile?.isVerifiedUser != true) {
                                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }

                    // My Activity Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("My Activity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem(label = "Reports", count = viewModel.userProfile?.totalReports ?: 0, icon = Icons.Default.Edit)
                                StatItem(label = "Added", count = viewModel.userProfile?.totalAdded ?: 0, icon = Icons.Default.AddLocation)
                                StatItem(label = "Verified", count = viewModel.userProfile?.totalVerifications ?: 0, icon = Icons.Default.Verified)
                            }
                        }
                    }

                    // Favorites Section
                    if (viewModel.favoriteRestrooms.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Favorites", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            viewModel.favoriteRestrooms.take(5).forEach { fav ->
                                NavigationDrawerItem(
                                    label = { Text(fav.name, maxLines = 1) },
                                    selected = false,
                                    onClick = {
                                        coroutineScope.launch {
                                            drawerState.close()
                                            val latLng = LatLng(fav.latitude, fav.longitude)
                                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                                            viewModel.loadFeedbackForCustom(
                                                CustomRestroom(id = fav.id, name = fav.name, category = fav.category, latitude = fav.latitude, longitude = fav.longitude)
                                            )
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Favorite, null, tint = Color.Red) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Account Actions Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        NavigationDrawerItem(
                            label = { Text(if (viewModel.currentUser != null) "Sign Out" else "Sign In") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                if (viewModel.currentUser != null) {
                                    viewModel.signOut()
                                    val intent = Intent(context, com.example.driverassist.login.LoginActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(context, com.example.driverassist.login.LoginActivity::class.java)
                                    context.startActivity(intent)
                                }
                            },
                            icon = { Icon(if (viewModel.currentUser != null) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login, null) }
                        )
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Navigation Drawer")
                            }
                        },
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
                                            { Icon(Icons.Default.Done, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                        } else null,
                                        enabled = !viewModel.isSearching
                                    )
                                }
                            }
                        },
                        actions = {
                            if (viewModel.isVerifiedFilterEnabled || viewModel.filterAccessible || viewModel.filterBabyChanging || viewModel.filterSingleStall) {
                                IconButton(onClick = {
                                    viewModel.isVerifiedFilterEnabled = false
                                    viewModel.filterAccessible = false
                                    viewModel.filterBabyChanging = false
                                    viewModel.filterSingleStall = false
                                }) {
                                    Icon(Icons.Default.FilterListOff, contentDescription = "Clear Filters")
                                }
                            }
                        }
                    )

                    if (viewModel.userProfile?.isVerifiedUser == true && viewModel.searchHistory.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    Text("Recents:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                items(viewModel.searchHistory) { query ->
                                    AssistChip(
                                        onClick = { viewModel.searchForBathrooms(placesClient, cameraPositionState.position.target, query) },
                                        label = { Text(query, style = MaterialTheme.typography.bodySmall) },
                                        leadingIcon = { Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp)) }
                                    )
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
                        viewModel.visibleGoogleRestrooms.forEach { place ->
                            place.location?.let { latLng ->
                                Marker(
                                    state = MarkerState(position = latLng),
                                    title = place.displayName,
                                    onClick = {
                                        viewModel.loadFeedbackForPlace(place)
                                        true
                                    }
                                )
                            }
                        }

                        viewModel.visibleCustomRestrooms.forEach { custom ->
                            Marker(
                                state = MarkerState(position = LatLng(custom.latitude, custom.longitude)),
                                title = custom.name,
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                                onClick = {
                                    viewModel.loadFeedbackForCustom(custom)
                                    true
                                }
                            )
                        }
                        
                        viewModel.pendingNewRestroomLocation?.let {
                            Marker(state = MarkerState(position = it), title = "New Location", alpha = 0.7f)
                        }
                    }

                    if (viewModel.showSearchThisArea) {
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.searchForBathrooms(placesClient, cameraPositionState.position.target, viewModel.selectedType) },
                            icon = { Icon(Icons.Default.Search, null) },
                            text = { Text(if (viewModel.isSearching) "Searching..." else "Search this area") },
                            expanded = !viewModel.isSearching,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)
                        )
                    }

                    if (viewModel.visibleGoogleRestrooms.isNotEmpty() || viewModel.visibleCustomRestrooms.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.findAndNavigateToNearestRestroom(context) },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
                        ) {
                            Text("Navigate to nearest")
                        }
                    }

                    // Interaction Dialogs (Feedback & Add Restroom)
                    if (viewModel.selectedRestroomId != null) {
                        RestroomDetailsDialog(viewModel = viewModel)
                    }

                    if (viewModel.pendingNewRestroomLocation != null) {
                        AddRestroomDialog(viewModel = viewModel)
                    }

                    // Map Controls
                    MapControls(
                        coroutineScope = coroutineScope,
                        cameraPositionState = cameraPositionState,
                        userLocation = viewModel.userLocation
                    )
                }
            }

            // Custom Edge Swipe Detector (Invisible)
            // This detects swipes from the extreme left edge to open the drawer
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(20.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            // Only trigger if swipe is clearly to the right and drawer is closed
                            if (dragAmount > 20f && drawerState.isClosed) {
                                coroutineScope.launch { drawerState.open() }
                            }
                        }
                    }
            )
        }
    }

    if (showFilterSheet) {
        AdvancedFilterSheet(viewModel = viewModel, onDismiss = { showFilterSheet = false })
    }

    if (viewModel.isInitialLoading) {
        LoadingScreen()
    }
}

@Composable
fun MapControls(
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    cameraPositionState: CameraPositionState,
    userLocation: LatLng?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(onClick = { coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) } }) {
                Icon(Icons.Default.Add, "Zoom In")
            }
            SmallFloatingActionButton(onClick = { coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) } }) {
                Icon(Icons.Default.Remove, "Zoom Out")
            }
        }

        FloatingActionButton(
            onClick = {
                userLocation?.let { coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f)) } }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 140.dp)
        ) {
            Icon(Icons.Default.MyLocation, "My Location")
        }
    }
}

@Composable
fun RestroomListView(
    viewModel: MapViewModel,
    onRestroomClick: (LatLng, Place?, CustomRestroom?) -> Unit
) {
    val googleRestrooms = viewModel.visibleGoogleRestrooms
    val customRestrooms = viewModel.visibleCustomRestrooms
    val userLocation = viewModel.userLocation

    val combinedList = remember(googleRestrooms, customRestrooms, userLocation) {
        val list = mutableListOf<RestroomListItemData>()
        googleRestrooms.forEach { place ->
            place.location?.let { list.add(RestroomListItemData(place.id ?: "", place.displayName ?: "Restroom", it, viewModel.selectedType, googlePlace = place)) }
        }
        customRestrooms.forEach { list.add(RestroomListItemData(it.id, it.name, LatLng(it.latitude, it.longitude), it.category, customRestroom = it)) }
        if (userLocation != null) list.sortedBy { com.example.driverassist.util.distanceMeters(userLocation, it.location) }.take(10) else list.take(10)
    }

    Column(modifier = Modifier.fillMaxHeight(0.8f).padding(horizontal = 16.dp)) {
        Text("Nearby Restrooms", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
        if (combinedList.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No restrooms found.") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(combinedList) { item ->
                    RestroomRow(item = item, aggregate = viewModel.restroomAggregates[item.id], userLocation = userLocation, onClick = { onRestroomClick(item.location, item.googlePlace, item.customRestroom) })
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
    val googlePlace: Place? = null,
    val customRestroom: CustomRestroom? = null
)

@Composable
fun RestroomRow(item: RestroomListItemData, aggregate: RestroomAggregate?, userLocation: LatLng?, onClick: () -> Unit) {
    val distance = userLocation?.let { com.example.driverassist.util.distanceMeters(it, item.location) }
    val now = System.currentTimeMillis()

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val categoryIcon = when {
                item.category.contains("coffee", ignoreCase = true) -> Icons.Default.Coffee
                item.category.contains("gas", ignoreCase = true) -> Icons.Default.LocalGasStation
                item.category.contains("fast food", ignoreCase = true) -> Icons.Default.Fastfood
                item.category.contains("restaurant", ignoreCase = true) -> Icons.Default.Restaurant
                item.category.contains("bar", ignoreCase = true) -> Icons.Default.LocalBar
                item.category.contains("mall", ignoreCase = true) -> Icons.Default.Storefront
                else -> Icons.Default.Wc
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(categoryIcon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    distance?.let {
                        Text(" • ", style = MaterialTheme.typography.bodySmall)
                        Text("${(it/1000).format(1)} km", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (aggregate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        val ratingColor = when {
                            aggregate.avgCleanliness >= 4.0 -> Color(0xFF2E7D32)
                            aggregate.avgCleanliness >= 2.5 -> Color(0xFFF57C00)
                            else -> Color(0xFFD32F2F)
                        }
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp), tint = ratingColor)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = aggregate.avgCleanliness.format(1),
                            style = MaterialTheme.typography.labelLarge,
                            color = ratingColor,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(Modifier.width(8.dp))
                        
                        if (aggregate.isRecentlyVerified(now)) {
                            Badge(containerColor = Color(0xFF2E7D32)) { Text("Verified", color = Color.White) }
                        } else if (aggregate.isDirtyNow(now)) {
                            Badge(containerColor = Color(0xFFD32F2F)) { Text("Dirty", color = Color.White) }
                        } else if (aggregate.isClosedNow(now)) {
                            Badge(containerColor = Color.Black) { Text("Closed", color = Color.White) }
                        }
                    }
                }
            }
            
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

@Composable
fun StatItem(label: String, count: Int, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun FeatureBadge(icon: ImageVector, label: String, containerColor: Color) {
    Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun FilterToggleRow(label: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestroomDetailsDialog(viewModel: MapViewModel) {
    var showFeedbackInputs by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = { viewModel.clearSelectedPlace(); showFeedbackInputs = false },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(viewModel.selectedRestroomName ?: "Restroom", modifier = Modifier.weight(1f))
                val isFav = viewModel.selectedRestroomId?.let { viewModel.isFavorite(it) } ?: false
                IconButton(onClick = { viewModel.toggleFavorite() }) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (isFav) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                viewModel.selectedAggregate?.let { RestroomAggregateSummary(it) }
                
                if (showFeedbackInputs) {
                    HorizontalDivider()
                    Text("Update Information", style = MaterialTheme.typography.titleSmall)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cleanliness", modifier = Modifier.weight(1f))
                            Row {
                                (1..5).forEach { rating ->
                                    IconButton(onClick = { viewModel.selectedCleanlinessRating = rating }) {
                                        Icon(
                                            imageVector = if (viewModel.selectedCleanlinessRating >= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null,
                                            tint = if (viewModel.selectedCleanlinessRating >= rating) Color(0xFFFBC02D) else Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                        
                        OutlinedTextField(
                            value = viewModel.userNoteUpdate,
                            onValueChange = { viewModel.userNoteUpdate = it },
                            label = { Text("Add a note (passcode, etc.)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        FilterToggleRow("Requires Passcode", Icons.Default.Lock, viewModel.needsPasscodeUpdate) { viewModel.needsPasscodeUpdate = it }
                        FilterToggleRow("Truck Friendly", Icons.Default.LocalShipping, viewModel.isTruckFriendlyUpdate) { viewModel.isTruckFriendlyUpdate = it }
                        FilterToggleRow("Accessible", Icons.AutoMirrored.Filled.Accessible, viewModel.isAccessibleUpdate) { viewModel.isAccessibleUpdate = it }
                        FilterToggleRow("Baby Changing", Icons.Default.ChildCare, viewModel.hasBabyChangingUpdate) { viewModel.hasBabyChangingUpdate = it }
                        FilterToggleRow("Single Stall", Icons.Default.Person, viewModel.isSingleStallUpdate) { viewModel.isSingleStallUpdate = it }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = viewModel.markedDirtyUpdate,
                                onClick = { viewModel.markedDirtyUpdate = !viewModel.markedDirtyUpdate },
                                label = { Text("Dirty") },
                                leadingIcon = if (viewModel.markedDirtyUpdate) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFD32F2F).copy(alpha = 0.2f))
                            )
                            FilterChip(
                                selected = viewModel.markedClosedUpdate,
                                onClick = { viewModel.markedClosedUpdate = !viewModel.markedClosedUpdate },
                                label = { Text("Closed") },
                                leadingIcon = if (viewModel.markedClosedUpdate) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Black.copy(alpha = 0.2f))
                            )
                        }
                    }
                    
                    Button(
                        onClick = { viewModel.submitFeedback(true); showFeedbackInputs = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Submit Feedback")
                    }
                } else {
                    Button(
                        onClick = { viewModel.confirmStillClean() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(Icons.Default.ThumbUp, null)
                        Spacer(Modifier.width(8.dp))
                        Text("It's Clean!")
                    }
                    
                    OutlinedButton(
                        onClick = { showFeedbackInputs = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Update Details / Feedback")
                    }

                    TextButton(
                        onClick = { viewModel.flagAsIncorrect() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Not a Restroom")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                viewModel.selectedRestroomLocation?.let { loc ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}")).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    context.startActivity(intent)
                }
            }) { Text("Navigate") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.clearSelectedPlace() }) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestroomAggregateSummary(aggregate: RestroomAggregate) {
    val now = System.currentTimeMillis()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ratingColor = when {
                aggregate.avgCleanliness >= 4.0 -> Color(0xFF2E7D32)
                aggregate.avgCleanliness >= 2.5 -> Color(0xFFF57C00)
                else -> Color(0xFFD32F2F)
            }
            Text(
                text = aggregate.avgCleanliness.format(1),
                style = MaterialTheme.typography.displaySmall,
                color = ratingColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Cleanliness Score", style = MaterialTheme.typography.labelMedium)
                Text("Based on ${aggregate.ratingCount} reports", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (aggregate.isDirtyNow(now)) {
            Surface(
                color = Color(0xFFD32F2F).copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFD32F2F))
                    Spacer(Modifier.width(12.dp))
                    Text("Reported dirty recently. Proceed with caution.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD32F2F))
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (aggregate.needsPasscode) FeatureBadge(Icons.Default.Lock, "Code Required", Color.Gray.copy(alpha = 0.1f))
            if (aggregate.isTruckFriendly) FeatureBadge(Icons.Default.LocalShipping, "Truck Friendly", Color.Blue.copy(alpha = 0.1f))
            if (aggregate.isAccessible) FeatureBadge(Icons.AutoMirrored.Filled.Accessible, "Accessible", Color.Blue.copy(alpha = 0.1f))
            if (aggregate.hasBabyChanging) FeatureBadge(Icons.Default.ChildCare, "Changing Table", Color.Magenta.copy(alpha = 0.1f))
            if (aggregate.isSingleStall) FeatureBadge(Icons.Default.Person, "Single Stall", Color.Cyan.copy(alpha = 0.1f))
        }
        
        if (aggregate.note.isNotBlank()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Latest Note", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(aggregate.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRestroomDialog(viewModel: MapViewModel) {
    var expanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = { viewModel.pendingNewRestroomLocation = null },
        title = { Text("Add New Restroom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = viewModel.newRestroomName,
                    onValueChange = { viewModel.newRestroomName = it },
                    label = { Text("Name (e.g. Starbucks, Public Park)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = viewModel.newRestroomCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        viewModel.restroomTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    viewModel.newRestroomCategory = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = viewModel.newRestroomNote,
                    onValueChange = { viewModel.newRestroomNote = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Features", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                
                FilterToggleRow("Requires Passcode", Icons.Default.Lock, viewModel.newRestroomNeedsPasscode) { viewModel.newRestroomNeedsPasscode = it }
                FilterToggleRow("Truck Friendly", Icons.Default.LocalShipping, viewModel.newRestroomIsTruckFriendly) { viewModel.newRestroomIsTruckFriendly = it }
                FilterToggleRow("Accessible", Icons.AutoMirrored.Filled.Accessible, viewModel.newRestroomIsAccessible) { viewModel.newRestroomIsAccessible = it }
                FilterToggleRow("Baby Changing", Icons.Default.ChildCare, viewModel.newRestroomHasBabyChanging) { viewModel.newRestroomHasBabyChanging = it }
                FilterToggleRow("Single Stall", Icons.Default.Person, viewModel.newRestroomIsSingleStall) { viewModel.newRestroomIsSingleStall = it }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.saveCustomRestroom() }) {
                Text("Save Restroom")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.pendingNewRestroomLocation = null }) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFilterSheet(viewModel: MapViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Advanced Filters", style = MaterialTheme.typography.titleLarge)
            FilterToggleRow("Accessible", Icons.AutoMirrored.Filled.Accessible, viewModel.filterAccessible) { viewModel.filterAccessible = it }
            FilterToggleRow("Baby Changing", Icons.Default.ChildCare, viewModel.filterBabyChanging) { viewModel.filterBabyChanging = it }
            FilterToggleRow("Single Stall", Icons.Default.Lock, viewModel.filterSingleStall) { viewModel.filterSingleStall = it }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Apply") }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(Modifier.padding(bottom = 32.dp))
            CircularProgressIndicator()
        }
    }
}
