package com.marketnewsmonitor.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.marketnewsmonitor.app.MainActivity
import com.marketnewsmonitor.app.R
import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker

/**
 * One grouped notification per ticker per poll ("3 new articles for AAPL" +
 * top headline) rather than one per article — a busy news day shouldn't
 * spam notifications. Tapping opens the app to the Dashboard; no deep link
 * to the specific ticker in this phase.
 */
class NotificationHelper(private val context: Context) : ArticleNotifier {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "News alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "New articles for watchlisted tickers" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun notifyNewArticles(ticker: Ticker, articles: List<Article>): Boolean {
        if (articles.isEmpty() || !hasPostPermission()) return false

        val title = if (articles.size == 1) {
            "New article for ${ticker.symbol}"
        } else {
            "${articles.size} new articles for ${ticker.symbol}"
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ticker.symbol.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(articles.first().headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(articles.joinToString("\n") { it.headline }))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(ticker.symbol.hashCode(), notification)
        return true
    }

    private fun hasPostPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "news_alerts"
    }
}
