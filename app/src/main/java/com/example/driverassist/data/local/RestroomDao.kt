package com.example.driverassist.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RestroomDao {
    @Query("SELECT * FROM offline_restrooms ORDER BY timestamp DESC")
    fun getAllOfflineRestrooms(): Flow<List<OfflineRestroom>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestroom(restroom: OfflineRestroom)

    @Query("DELETE FROM offline_restrooms WHERE id = :id")
    suspend fun deleteById(id: String)

    // Favorites
    @Query("SELECT * FROM favorite_restrooms ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteRestroom>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteRestroom)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteRestroom)

    @Query("DELETE FROM favorite_restrooms WHERE id = :id")
    suspend fun deleteFavoriteById(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_restrooms WHERE id = :id)")
    fun isFavorite(id: String): Flow<Boolean>
}
