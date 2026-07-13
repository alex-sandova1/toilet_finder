package com.example.driverassist.model

import com.google.android.gms.maps.model.LatLng

/**
 * Data class representing the details of a route fetched from the Directions API.
 * @param points A list of LatLng points defining the polyline of the route.
 * @param distanceText A human-readable string for the total distance of the route.
 * @param durationText A human-readable string for the estimated duration of the route.
 */
data class RouteDetails(
    val points: List<LatLng>,
    val distanceText: String,
    val durationText: String
)
