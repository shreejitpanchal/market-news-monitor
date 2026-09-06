package com.marketnewsmonitor.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.marketnewsmonitor.app.data.local.dao.ArticleDao
import com.marketnewsmonitor.app.data.local.dao.SourceDao
import com.marketnewsmonitor.app.data.local.dao.TickerDao
import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Source
import com.marketnewsmonitor.app.data.local.entity.Ticker

@Database(
    entities = [Ticker::class, Article::class, Source::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tickerDao(): TickerDao
    abstract fun articleDao(): ArticleDao
    abstract fun sourceDao(): SourceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "market_news_monitor.db")
                // Pre-release stage (verify.md): no shipped installs to migrate yet.
                .fallbackToDestructiveMigration()
                .build()
    }
}
