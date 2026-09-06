package com.marketnewsmonitor.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marketnewsmonitor.app.data.local.entity.Ticker
import kotlinx.coroutines.flow.Flow

@Dao
interface TickerDao {
    @Query("SELECT * FROM tickers ORDER BY symbol ASC")
    fun observeAll(): Flow<List<Ticker>>

    @Query("SELECT * FROM tickers ORDER BY symbol ASC")
    suspend fun getAll(): List<Ticker>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ticker: Ticker)

    @Delete
    suspend fun delete(ticker: Ticker)

    @Query("DELETE FROM tickers")
    suspend fun deleteAll()

    @Query("UPDATE tickers SET muted = :muted WHERE symbol = :symbol")
    suspend fun setMuted(symbol: String, muted: Boolean)
}
