package com.phisherman.kavach.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DecisionDao {
    @Insert
    suspend fun insert(decision: Decision): Long

    @Query("UPDATE decisions SET userAction = :action WHERE id = :id")
    suspend fun setAction(id: Long, action: String)

    @Query("SELECT * FROM decisions ORDER BY ts DESC LIMIT :limit")
    fun recent(limit: Int = 200): Flow<List<Decision>>

    @Query("SELECT * FROM decisions WHERE ts >= :since ORDER BY ts DESC")
    suspend fun since(since: Long): List<Decision>

    @Query("SELECT * FROM decisions WHERE id = :id")
    suspend fun byId(id: Long): Decision?

    @Query("SELECT COUNT(*) FROM decisions WHERE level != 'SAFE'")
    fun flaggedCount(): Flow<Int>

    @Query("DELETE FROM decisions")
    suspend fun clear()
}
