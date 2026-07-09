package com.example.driverassist.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a restroom added by the user, stored locally for offline access.
 */
@Entity(tableName = "offline_restrooms")
data class OfflineRestroom(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)
