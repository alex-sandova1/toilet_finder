package com.example.driverassist.ui.map

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.driverassist.data.RestroomFeedbackRepository
import com.example.driverassist.data.UserRepository
import com.example.driverassist.data.local.OfflineRestroom
import com.example.driverassist.data.local.RestroomDatabase
import com.example.driverassist.model.*
import com.example.driverassist.network.fetchDrivingRoute
import com.example.driverassist.util.distanceMeters
import com.example.driverassist.util.findNearestBathroom
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MapViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val feedbackRepository = RestroomFeedbackRepository()
    private val database = RestroomDatabase.getDatabase(application)
    private val restroomDao = database.restroomDao()
    private val userRepository = UserRepository()
    private val auth = FirebaseAuth.getInstance()

    val currentUser get() = auth.currentUser
    var userProfile by mutableStateOf<UserProfile?>(null)
        private set

    var localOfflineRestrooms by mutableStateOf<List<OfflineRestroom>>(emptyList())
        private set

    init {
        loadUserProfile()
        // Collect local restrooms from Room for offline access
        viewModelScope.launch {
            restroomDao.getAllOfflineRestrooms().collect { localRestrooms ->
                localOfflineRestrooms = localRestrooms
            }
        }
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            userProfile = userRepository.fetchUserProfile(uid)
        }
    }

    val restroomTypes = listOf(
        "Public Restroom", "fast food restaurant restroom", "gas station restroom",
        "coffee shop restroom", "restaurant restroom", "bar restroom", "mall restroom"
    )

    // UI State
    var selectedTypeIndex by mutableIntStateOf(0)
        private set
    val selectedType get() = restroomTypes[selectedTypeIndex]

    var hasLocationPermission by mutableStateOf(false)
    var userLocation by mutableStateOf<LatLng?>(null)
    var bathroomLocations by mutableStateOf<List<Place>>(emptyList())
        private set
    var customRestrooms by mutableStateOf<List<com.example.driverassist.model.CustomRestroom>>(emptyList())
        private set
    var incorrectRestroomIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var categoryOverrides by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var restroomAggregates by mutableStateOf<Map<String, RestroomAggregate>>(emptyMap())
        private set

    val visibleGoogleRestrooms: List<Place>
        get() = bathroomLocations.filter { place ->
            val id = feedbackRepository.documentIdForPlace(place)
            if (incorrectRestroomIds.contains(id)) return@filter false
            
            // Premium Filter: Hide dirty or poorly rated restrooms
            if (isVerifiedFilterEnabled) {
                val agg = restroomAggregates[id]
                if (agg != null) {
                    val now = System.currentTimeMillis()
                    // Filter out if reported dirty now OR if average cleanliness is poor (< 3.0)
                    if (agg.isDirtyNow(now)) return@filter false
                    if (agg.ratingCount > 0 && agg.avgCleanliness < 3.0) return@filter false
                }
            }

            // If the community assigned a different category, check if it still matches the current filter
            val override = categoryOverrides[id]
            if (override != null && override.isNotBlank()) {
                return@filter override.equals(selectedType, ignoreCase = true)
            }
            true
        }

    val visibleCustomRestrooms: List<com.example.driverassist.model.CustomRestroom>
        get() {
            // Start with community restrooms from Firestore
            val communityList = customRestrooms.toMutableList()
            
            // Add local restrooms from Room that aren't already in the list
            localOfflineRestrooms.forEach { offline ->
                if (communityList.none { it.id == offline.id }) {
                    communityList.add(
                        com.example.driverassist.model.CustomRestroom(
                            id = offline.id,
                            name = offline.name,
                            category = offline.category,
                            note = offline.note,
                            latitude = offline.latitude,
                            longitude = offline.longitude
                        )
                    )
                }
            }

            return communityList.filter { custom ->
                // Filter by deletion flag
                if (custom.isDeleted) return@filter false
                
                // Premium Filter: Hide dirty or poorly rated custom restrooms
                if (isVerifiedFilterEnabled) {
                    val agg = restroomAggregates[custom.id]
                    if (agg != null) {
                        val now = System.currentTimeMillis()
                        if (agg.isDirtyNow(now)) return@filter false
                        if (agg.ratingCount > 0 && agg.avgCleanliness < 3.0) return@filter false
                    }
                }
                
                // Filter by category
                val matchesCategory = custom.category.equals(selectedType, ignoreCase = true)
                if (!matchesCategory) return@filter false

                // Then filter by distance to Google Places to prevent duplicates
                val customLatLng = LatLng(custom.latitude, custom.longitude)
                visibleGoogleRestrooms.none { googlePlace ->
                    googlePlace.location?.let { googleLatLng ->
                        distanceMeters(customLatLng, googleLatLng) < 20.0
                    } ?: false
                }
            }
        }

    var showSearchThisArea by mutableStateOf(false)
    var isSearching by mutableStateOf(false)
        private set
    var isInitialLoading by mutableStateOf(true)
        private set
    var isVerifiedFilterEnabled by mutableStateOf(false)
    var isLoadingRoute by mutableStateOf(false)
        private set

    // Generic state for whatever restroom is selected
    var selectedRestroomId by mutableStateOf<String?>(null)
    var selectedRestroomName by mutableStateOf<String?>(null)
    var selectedRestroomLocation by mutableStateOf<LatLng?>(null)

    var pendingNewRestroomLocation by mutableStateOf<LatLng?>(null)
    var newRestroomName by mutableStateOf("")
    var newRestroomCategory by mutableStateOf("Public Restroom")
    var newRestroomNote by mutableStateOf("")

    var selectedPlace by mutableStateOf<Place?>(null)
        private set
    var selectedAggregate by mutableStateOf<RestroomAggregate?>(null)
        private set
    var isLoadingFeedback by mutableStateOf(false)
        private set
    var isSubmittingFeedback by mutableStateOf(false)
        private set
    var feedbackErrorMessage by mutableStateOf<String?>(null)
        private set
    var selectedCleanlinessRating by mutableIntStateOf(3)
    var userNoteUpdate by mutableStateOf("")
    var userCategoryUpdate by mutableStateOf("")
    var markedDirtyUpdate by mutableStateOf(false)
    var markedClosedUpdate by mutableStateOf(false)

    var activeRoute by mutableStateOf<RouteDetails?>(null)
        private set
    var activeRouteDestinationName by mutableStateOf<String?>(null)
        private set
    var activeRouteDestinationLocation by mutableStateOf<LatLng?>(null)
        private set

    // Side effect state for Toasts
    var toastMessage by mutableStateOf<String?>(null)
        private set

    fun clearToastMessage() { toastMessage = null }

    fun updateSelectedType(index: Int, placesClient: PlacesClient, center: LatLng) {
        selectedTypeIndex = index
        searchForBathrooms(placesClient, center, selectedType)
    }

    fun toggleVerifiedFilter() {
        if (userProfile?.isVerifiedUser == true) {
            isVerifiedFilterEnabled = !isVerifiedFilterEnabled
        } else {
            toastMessage = "Upgrade to Verified User to use this filter!"
        }
    }

    fun clearSelectedPlace() {
        selectedRestroomId = null
        selectedRestroomName = null
        selectedRestroomLocation = null
        selectedPlace = null
        selectedAggregate = null
        isLoadingFeedback = false
        isSubmittingFeedback = false
        feedbackErrorMessage = null
        selectedCleanlinessRating = 3
        userNoteUpdate = ""
        userCategoryUpdate = ""
        markedDirtyUpdate = false
        markedClosedUpdate = false
    }

    fun loadFeedbackForPlace(place: Place) {
        val id = feedbackRepository.documentIdForPlace(place)
        selectedPlace = place
        selectedRestroomId = id
        selectedRestroomName = place.displayName ?: "Restroom"
        selectedRestroomLocation = place.location
        userCategoryUpdate = "" 
        
        loadFeedback(id)
    }

    fun loadFeedbackForCustom(custom: com.example.driverassist.model.CustomRestroom) {
        selectedPlace = null
        selectedRestroomId = custom.id
        selectedRestroomName = custom.name
        selectedRestroomLocation = LatLng(custom.latitude, custom.longitude)
        userCategoryUpdate = custom.category
        
        loadFeedback(custom.id)
    }

    private fun loadFeedback(id: String) {
        selectedAggregate = null
        feedbackErrorMessage = null
        selectedCleanlinessRating = 3
        markedDirtyUpdate = false
        markedClosedUpdate = false
        isLoadingFeedback = true

        viewModelScope.launch {
            runCatching {
                feedbackRepository.fetchAggregate(id)
            }.onSuccess { aggregate ->
                selectedAggregate = aggregate
                if (aggregate?.suggestedCategory?.isNotBlank() == true) {
                    userCategoryUpdate = aggregate.suggestedCategory
                }
            }.onFailure { error ->
                feedbackErrorMessage = error.message ?: "Unable to load community status right now."
            }
            isLoadingFeedback = false
        }
    }

    fun submitFeedback(includeRating: Boolean = false) {
        val id = selectedRestroomId ?: return
        val name = selectedRestroomName ?: "Restroom"
        val note = if (userNoteUpdate.isNotBlank()) userNoteUpdate else null
        val category = if (userCategoryUpdate.isNotBlank()) userCategoryUpdate else null
        val isCustom = selectedPlace == null
        
        isSubmittingFeedback = true
        feedbackErrorMessage = null

        viewModelScope.launch {
            runCatching {
                if (isCustom && category != null) {
                    feedbackRepository.updateCustomRestroomCategory(id, category)
                }
                
                feedbackRepository.submitReport(
                    id = id,
                    name = name,
                    report = RestroomReportInput(
                        cleanlinessRating = selectedCleanlinessRating.takeIf { includeRating },
                        markedDirty = markedDirtyUpdate,
                        markedClosed = markedClosedUpdate,
                        note = note,
                        suggestedCategory = category
                    )
                )
            }.onSuccess { updated ->
                selectedAggregate = updated
                userNoteUpdate = ""
                markedDirtyUpdate = false
                markedClosedUpdate = false
                toastMessage = "Update saved."
                
                // Update local aggregates map for filtering
                restroomAggregates = restroomAggregates.toMutableMap().apply {
                    put(id, updated)
                }
                
                // Update local state immediately to prevent "flickering"
                if (isCustom && category != null) {
                    customRestrooms = customRestrooms.map { 
                        if (it.id == id) it.copy(category = category) else it 
                    }
                } else if (!isCustom && category != null) {
                    categoryOverrides = categoryOverrides.toMutableMap().apply {
                        put(id, category)
                    }
                }
            }.onFailure { error ->
                feedbackErrorMessage = error.message ?: "Unable to save your update right now."
            }
            isSubmittingFeedback = false
        }
    }

    fun searchForBathrooms(placesClient: PlacesClient, center: LatLng, query: String) {
        isSearching = true
        
        // Fetch custom restrooms, incorrect IDs, and category overrides from Firestore
        viewModelScope.launch {
            runCatching {
                val customs = feedbackRepository.fetchCustomRestrooms()
                val incorrects = feedbackRepository.fetchIncorrectRestroomIds()
                val overrides = feedbackRepository.fetchCategoryOverrides()
                Triple(customs, incorrects, overrides)
            }.onSuccess { (customs, incorrects, overrides) ->
                customRestrooms = customs
                incorrectRestroomIds = incorrects.toSet()
                categoryOverrides = overrides
            }
        }

        val placeFields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION)
        val searchRequest = SearchByTextRequest.builder(query, placeFields)
            .setLocationBias(CircularBounds.newInstance(center, 5000.0))
            .setMaxResultCount(20)
            .build()

        placesClient.searchByText(searchRequest)
            .addOnSuccessListener { response ->
                bathroomLocations = response.places
                
                // Fetch aggregate data for all found restrooms
                val allIds = bathroomLocations.map { feedbackRepository.documentIdForPlace(it) } + 
                            customRestrooms.map { it.id }
                viewModelScope.launch {
                    val aggregates = feedbackRepository.fetchAggregates(allIds)
                    restroomAggregates = aggregates
                }

                clearRoute()
                clearSelectedPlace()
                isSearching = false
                isInitialLoading = false
                showSearchThisArea = false
            }
            .addOnFailureListener { exception ->
                Log.e("Search", "Failed: ${exception.message}")
                isSearching = false
                isInitialLoading = false
            }
    }

    fun fetchRoute(origin: LatLng, mapsApiKey: String) {
        val nearest = findNearestBathroom(origin, bathroomLocations) ?: return
        val dest = nearest.location ?: return
        isLoadingRoute = true
        viewModelScope.launch {
            val route = fetchDrivingRoute(mapsApiKey, origin, dest)
            isLoadingRoute = false
            if (route != null) {
                activeRoute = route
                activeRouteDestinationName = nearest.displayName ?: "Nearest restroom"
                activeRouteDestinationLocation = dest
            } else {
                toastMessage = "Unable to load route"
            }
        }
    }

    fun clearRoute() {
        activeRoute = null
        activeRouteDestinationName = null
        activeRouteDestinationLocation = null
    }

    fun signOut() {
        auth.signOut()
        clearSelectedPlace()
    }

    fun onCameraMoved() {
        if (!isSearching) {
            showSearchThisArea = true
        }
    }

    fun onMapLongClick(latLng: LatLng) {
        pendingNewRestroomLocation = latLng
        newRestroomName = ""
        newRestroomCategory = selectedType // Default to current filter
        newRestroomNote = ""
    }

    fun saveCustomRestroom() {
        val location = pendingNewRestroomLocation ?: return
        val name = newRestroomName.ifBlank { "New Restroom" }
        val category = newRestroomCategory
        val note = newRestroomNote
        
        isSearching = true // Reuse as loading state
        viewModelScope.launch {
            runCatching {
                feedbackRepository.saveCustomRestroom(name, category, note, location)
            }.onSuccess { id ->
                toastMessage = "Restroom added to community map!"
                pendingNewRestroomLocation = null
                
                // Save to Room for offline access
                viewModelScope.launch {
                    restroomDao.insertRestroom(
                        OfflineRestroom(
                            id = id,
                            name = name,
                            category = category,
                            note = note,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    )
                }

                // Refresh list
                customRestrooms = feedbackRepository.fetchCustomRestrooms()
            }.onFailure {
                toastMessage = "Failed to add restroom. Try again."
            }
            isSearching = false
        }
    }

    fun deleteRestroom() {
        val id = selectedRestroomId ?: return
        val isCustom = selectedPlace == null
        
        isSubmittingFeedback = true
        viewModelScope.launch {
            runCatching {
                if (isCustom) {
                    feedbackRepository.deleteCustomRestroom(id)
                    // Also delete from local Room database
                    restroomDao.deleteById(id)
                } else {
                    feedbackRepository.submitReport(
                        id = id,
                        name = selectedRestroomName ?: "Restroom",
                        report = RestroomReportInput(markedIncorrect = true)
                    )
                }
            }.onSuccess {
                toastMessage = if (isCustom) "Restroom deleted." else "Restroom reported as incorrect."
                if (isCustom) {
                    customRestrooms = feedbackRepository.fetchCustomRestrooms()
                } else {
                    incorrectRestroomIds = feedbackRepository.fetchIncorrectRestroomIds().toSet()
                }
                clearSelectedPlace()
            }.onFailure {
                toastMessage = "Unable to process request."
            }
            isSubmittingFeedback = false
        }
    }
}
