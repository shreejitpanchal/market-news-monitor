package com.marketnewsmonitor.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Schema defined now per the Phase 0 roadmap; populated starting Phase 2 (live news feed). */
@Entity(tableName = "sources")
data class Source(
    @PrimaryKey val id: String,
    val displayName: String,
    val type: String,
)
