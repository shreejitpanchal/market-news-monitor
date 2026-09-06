package com.marketnewsmonitor.webapp

/**
 * Own small data classes for this preview module — not imports from `app`'s
 * Room entities, since this Wasm/JS module can't depend on an Android-only
 * module. Everything here is hardcoded sample content; nothing is fetched,
 * persisted, or sent anywhere.
 */
data class SampleTicker(val symbol: String, val companyName: String, val urgency: Urgency, val muted: Boolean = false)

enum class Urgency(val label: String) { HOT("HOT"), WARM("WARM"), CALM("CALM") }

data class SampleArticle(
    val headline: String,
    val source: String,
    val urgency: Urgency,
    val whyItMatters: String,
    val dateLabel: String,
)

data class SamplePricePoint(val dayIndex: Int, val close: Double)

object SampleData {
    val tickers = listOf(
        SampleTicker("AAPL", "Apple Inc.", Urgency.HOT),
        SampleTicker("TSLA", "Tesla, Inc.", Urgency.WARM),
        SampleTicker("NVDA", "NVIDIA Corporation", Urgency.HOT),
        SampleTicker("MSFT", "Microsoft Corporation", Urgency.CALM),
        SampleTicker("AMZN", "Amazon.com, Inc.", Urgency.CALM, muted = true),
    )

    val articlesBySymbol: Map<String, List<SampleArticle>> = mapOf(
        "AAPL" to listOf(
            SampleArticle(
                "Apple beats Q3 earnings estimates, raises guidance",
                "Reuters",
                Urgency.HOT,
                "Guidance raise suggests demand is stronger than the Street expected.",
                "Sep 5",
            ),
            SampleArticle(
                "Apple announces expanded stock buyback program",
                "Bloomberg",
                Urgency.WARM,
                "Buyback signals management confidence, modest EPS tailwind.",
                "Sep 3",
            ),
        ),
        "TSLA" to listOf(
            SampleArticle(
                "Tesla delivery numbers miss analyst expectations",
                "CNBC",
                Urgency.WARM,
                "Delivery miss could pressure the stock at the open.",
                "Sep 4",
            ),
        ),
        "NVDA" to listOf(
            SampleArticle(
                "NVIDIA unveils next-gen AI chip, stock jumps in after-hours",
                "The Verge",
                Urgency.HOT,
                "New chip announcement often moves the stock sharply the next session.",
                "Sep 6",
            ),
        ),
        "MSFT" to listOf(
            SampleArticle(
                "Microsoft adds new Copilot features across Office suite",
                "TechCrunch",
                Urgency.CALM,
                "Incremental product update, unlikely to move the stock materially.",
                "Sep 2",
            ),
        ),
        "AMZN" to emptyList(),
    )

    /** A simple deterministic wave so every ticker's sample chart looks distinct. */
    fun priceHistory(symbol: String): List<SamplePricePoint> {
        val seed = symbol.sumOf { it.code }
        val base = 100.0 + (seed % 50)
        return (0 until 60).map { day ->
            val wave = kotlin.math.sin((day + seed) / 6.0) * (base * 0.05)
            val drift = day * (0.1 + (seed % 5) * 0.02)
            SamplePricePoint(day, base + wave + drift)
        }
    }
}
