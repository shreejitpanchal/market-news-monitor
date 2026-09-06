package com.marketnewsmonitor.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper over WorkManager — enqueue/cancel the single named daily
 * digest job, fixed at 8:00 AM device-local time (no Settings time picker
 * this pass). Subject to WorkManager periodic work's usual drift; that's an
 * acceptable trade for a personal "before market open" nudge, not a hard
 * deadline.
 */
object DigestScheduler {
    private const val WORK_NAME = "daily_digest"
    private val TARGET_TIME: LocalTime = LocalTime.of(8, 0)

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DigestWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNextTarget(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun millisUntilNextTarget(): Long {
        val now = ZonedDateTime.now()
        var next = now.with(TARGET_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
