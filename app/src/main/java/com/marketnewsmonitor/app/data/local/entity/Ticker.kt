package com.marketnewsmonitor.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tickers")
data class Ticker(
    @PrimaryKey val symbol: String,
    val companyName: String?,
    val addedAt: Long,
)
