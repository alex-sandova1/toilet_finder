package com.example.driverassist.util

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place

// Calculates distance between coordinates.
fun distanceMeters(from: LatLng, to: LatLng): Double {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
    return results[0].toDouble()
}

// Finds the closest restroom.
fun findNearestBathroom(from: LatLng, places: List<Place>): Place? {
    var nearest: Place? = null
    var nearestDistance = Double.MAX_VALUE
    places.forEach { place ->
        val location = place.location ?: return@forEach
        val distance = distanceMeters(from, location)
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearest = place
        }
    }
    return nearest
}
