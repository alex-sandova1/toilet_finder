package com.example.driverassist.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a restroom favorited by the user.
 */
@Entity(tableName = "favorite_restrooms")
data class FavoriteRestroom(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)
