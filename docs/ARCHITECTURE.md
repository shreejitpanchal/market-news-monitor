# Architecture

The structural counterpart to [README.md](../README.md)'s feature tour —
system context, data flow, the data-source comparison, and the phased
roadmap, plus the reasoning behind each decision. See
[CLAUDE.md](../CLAUDE.md) for the condensed "don't re-litigate this"
version.

## 1. System context

Fully on-device, single user, no backend server, no accounts.

```mermaid
flowchart TB
    user["You (trader)"]
    subgraph device["Android phone"]
        ui["Compose UI\nDashboard / Watchlist / Ticker detail / Settings"]
        vm["ViewModels\nStateFlow per screen"]
        repo["Repositories\nnews, tickers, AI classification"]
        room[("Room DB\ntickers, cached articles,\nnotified-state")]
        worker["WorkManager\nperiodic poll -- interval configurable,\ncan be disabled in Settings"]
        keystore[("EncryptedSharedPreferences\nClaude API key")]
    end
    subgraph external["External (free tier)"]
        finnhub["Finnhub API"]
        rss["RSS: Yahoo Finance,\nReuters, MarketWatch"]
        edgar["SEC EDGAR"]
        claude["Claude API\nHaiku (per-article) + Sonnet (daily digest)"]
    end

    user -->|uses| ui
    ui --> vm --> repo
    repo --> room
    worker --> repo
    worker -->|local notification| user
    ui -->|foreground fetch on app open,\nalways runs regardless of\nbackground-poll setting| repo
    repo --> finnhub & rss & edgar
    repo -->|classify new articles| claude
    repo --> keystore
```

**Background polling is a Settings-controlled feature, not a fixed
background job:** the interval defaults to 15 minutes (Android's own
floor for periodic `WorkManager` work) but is adjustable, and can be
turned off entirely — Settings enqueues or cancels the `WorkManager`
job rather than it running unconditionally from install. Separately,
opening the app always triggers an immediate foreground fetch through
the same repositories, bypassing `WorkManager` entirely, so the
dashboard is never stale on open even with background polling off.

News sources follow a small engine/registry pattern rather than one
fetcher with source-specific branches inside it — one class per source
(`FinnhubSource`, `RssSource`, `EdgarSource`) behind a shared interface,
looked up from a registry. This mirrors how `coding-adventure` (this
project's sibling repo, used as a structural reference) maps one
`ExecutionEngine` subclass per language behind `app/execution/registry.py`
— adding a data source later means adding one class, not editing a
shared fetcher's branches.

No server anywhere in the path — the periodic worker and the notification
it fires both happen on the same device, which is why "push notifications
matter" (a locked-in decision) didn't require standing up a push service.

## 2. Data-source comparison

Free tier, chosen for *latency* — the trap with "free news API" is that
several throttle or delay exactly the thing that matters for trading
relevance.

| Source | Gives you | Free tier | Use in alerting path? |
|---|---|---|---|
| Finnhub | Company news, basic financials | 60 calls/min | Yes — primary source |
| SEC EDGAR | 8-K, Form 4 filings | Unlimited | Yes — high-signal, low-noise |
| RSS (Google News, per-ticker search query) | Wire headlines per ticker | Unlimited | Yes — real-time, no key |
| Alpha Vantage | Built-in news sentiment score | 25 req/day | No — too limited to poll; reserve for on-demand deep dives |
| NewsAPI.org | Broad headline aggregation | 100 req/day | **No — 24h delay on free tier makes it useless for alerts.** Optional historical-context use only. |

The RSS row originally named Yahoo Finance/Reuters/MarketWatch specifically —
changed during Phase 2 implementation once it turned out none of the three
still reliably serves a public, per-ticker RSS feed (Reuters shut its public
RSS down entirely). Google News' per-query RSS
(`https://news.google.com/rss/search?q=<TICKER>+stock`) covers the same
"unlimited, no key" niche and actually returns results.

## 3. Claude integration

Two models, split by cost profile, not arbitrarily:

- **Haiku** — runs on every *new* article surfaced by the periodic worker
  (after de-dup against Room). Classifies urgency (hot/warm/calm), writes
  a one-line "why this might move the stock," and clusters near-duplicate
  stories from different outlets into one card.
- **Sonnet** — runs once a day for the pre-market digest, where output
  quality across the whole watchlist matters more than per-call cost.

The API key is entered once in Settings, stored in
`EncryptedSharedPreferences` (backed by the Android Keystore), and never
leaves the device except in direct API calls to `api.anthropic.com`.
Subscription-based auth (reusing a Claude.ai login) was considered and
rejected — not a supported third-party integration path.

## 4. Phased roadmap

Each phase ships something actually usable, not just a milestone.

1. **Project scaffold** — Kotlin/Compose project, package structure, Room
   schema (`Ticker`, `Article`, `Source`), bottom nav
   (Dashboard/Watchlist/Settings).
2. **Watchlist, no news yet** — add/remove/search tickers, dashboard
   renders tracked tickers as empty-state cards.
3. **Live news feed** — Finnhub + RSS ingestion into Room, ticker-detail
   feed with clickable source links, manual refresh.
4. **Background alerts** — WorkManager periodic poll + diffing against
   already-seen articles, local notifications with per-ticker mute.
5. **Claude comes online** — API key settings screen, per-article urgency
   badge + "why it matters," dedup clustering. Shipped: urgency badge +
   why-it-matters, via one batched Haiku call per ticker per refresh.
   **Dedup clustering deferred** — needs its own schema (a grouping) and
   merged-card UI on top of classification; cross-outlet duplicates
   currently just show as separate cards.
6. **Trading-specific depth** — SEC filings feed, earnings-calendar-aware
   alert sensitivity, pre-market digest notification, home-screen widget.
   **Shipped: the SEC filings feed** — `EdgarSource` is just another
   `NewsSource` (see §1's registry pattern), so filings flow through the
   existing Room table, classifier, notifications, and UI unchanged. The
   other three pieces (earnings-aware sensitivity, digest, widget) are
   still unbuilt.

## 5. Stretch ideas (not scheduled)

- Sentiment-vs-price overlay chart.
- Unusual options activity / large block trades layered alongside news.
- Backtest-lite: log every AI call vs. next-day price move to judge
  signal quality over time.
- Social sentiment (Reddit/StockTwits), clearly labeled lower-confidence
  than wire news.

## 6. Risks worth remembering

- **Not financial advice** — urgency scores are a triage aid; the app
  should surface uncertainty rather than false confidence.
- **Free-tier ceilings** — Finnhub's 60 calls/min comfortably covers a
  watchlist of a few dozen tickers; a much larger list may need a longer
  poll interval or a paid tier.
- **Estimated running cost** — $0 in data fees at free tier; Claude Haiku
  classification at modest daily article volume runs to roughly a few
  cents/day, Sonnet's daily digest adds a similar amount.
