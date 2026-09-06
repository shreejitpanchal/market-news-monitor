package com.marketnewsmonitor.app.data.notifications

/** Extracted so [com.marketnewsmonitor.app.work.DigestWorker] is testable without a real Context. */
interface DigestNotifier {
    /** Returns true if a notification was actually posted (false = permission denied). */
    fun notifyDigest(text: String): Boolean
}
