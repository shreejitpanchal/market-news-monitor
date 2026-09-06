package com.marketnewsmonitor.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marketnewsmonitor.app.data.local.entity.Source

/** Unused until Phase 2 (live news feed) — schema defined now per the roadmap. */
@Dao
interface SourceDao {
    @Query("SELECT * FROM sources")
    suspend fun getAll(): List<Source>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: Source)
}
