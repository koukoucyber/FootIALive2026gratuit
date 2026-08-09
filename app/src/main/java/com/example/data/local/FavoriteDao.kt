package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.FavoriteTeamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_teams ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteTeamEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_teams WHERE teamName = :teamName)")
    fun isFavorite(teamName: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(team: FavoriteTeamEntity)

    @Query("DELETE FROM favorite_teams WHERE teamName = :teamName")
    suspend fun deleteFavorite(teamName: String)
}
