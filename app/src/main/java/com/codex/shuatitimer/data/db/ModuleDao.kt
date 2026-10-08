package com.codex.shuatitimer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.codex.shuatitimer.data.model.StudyModule
import kotlinx.coroutines.flow.Flow

@Dao
interface ModuleDao {
    @Query("SELECT * FROM modules WHERE deleted = 0 ORDER BY updatedAt, name")
    fun observeActive(): Flow<List<StudyModule>>

    @Query("SELECT * FROM modules ORDER BY name")
    suspend fun all(): List<StudyModule>

    @Query("SELECT * FROM modules WHERE name = :name")
    suspend fun find(name: String): StudyModule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(module: StudyModule)

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM modules")
    suspend fun maxRevision(): Long
}
