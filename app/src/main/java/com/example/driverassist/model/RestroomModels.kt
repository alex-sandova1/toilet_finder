package com.example.driverassist.model

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import kotlin.math.abs

// Defines restroom review data.
data class RestroomReview(
    val placeId: String,
    val cleanlinessRating: Int,
    val codeRequired: Boolean?,
    val hoursText: String,
    val reviewText: String
)

// Defines route geometry and info.
data class RouteDetails(
    val points: List<LatLng>,
    val distanceText: String,
    val durationText: String
)

// Generates simulated restroom data.
fun buildLocalReview(place: Place): RestroomReview {
    val key = place.id ?: place.displayName ?: "unknown"
    val seed = abs(key.hashCode())
    val cleanliness = (seed % 5) + 1
    val codeRequired = when (seed % 3) {
        0 -> true
        1 -> false
        else -> null
    }
    val hoursText = listOf("Open 24 hours", "6:00 AM - 10:00 PM", "7:00 AM - 11:00 PM", "Hours not listed")[seed % 4]
    val reviewText = when (cleanliness) {
        5 -> "Very clean and regularly stocked."
        4 -> "Clean most visits with minor wait times."
        3 -> "Average cleanliness; bring hand sanitizer."
        2 -> "Below average cleanliness; use only if needed."
        else -> "Poor cleanliness based on recent reports."
    }
    return RestroomReview(key, cleanliness, codeRequired, hoursText, reviewText)
}

// Converts code boolean to text.
fun codeRequiredLabel(codeRequired: Boolean?): String = when (codeRequired) {
    true -> "Yes"
    false -> "No"
    null -> "Unknown"
}
