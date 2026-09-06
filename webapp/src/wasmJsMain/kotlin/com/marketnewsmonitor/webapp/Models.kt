package com.marketnewsmonitor.webapp

import kotlinx.serialization.Serializable

/**
 * Own small model types for this module — not imports from `app`'s Room
 * entities, since this Wasm/JS module can't depend on an Android-only
 * module. Simpler than the Android app's on purpose: no clustering, no
 * freshness-window/date-cutoff filtering, no notified-state — see
 * CLAUDE.md's webapp/server decision for the full list of what's cut and
 * why (mainly: avoiding date-arithmetic libraries this module doesn't
 * need, since there's no background polling to make freshness windows
 * meaningful here anyway).
 */
@Serializable
data class Ticker(val symbol: String, val companyName: String? = null, val muted: Boolean = false)

enum class Urgency { HOT, WARM, CALM }

data class Article(
    val id: String,
    val headline: String,
    val url: String,
    val source: String,
    val urgency: Urgency? = null,
    val whyItMatters: String? = null,
)

data class PricePoint(val date: String, val close: Double)

data class TickerSuggestion(val symbol: String, val name: String)
