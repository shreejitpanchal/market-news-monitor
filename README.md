# MarketNewsMonitor

A personal Android app that watches a stock/options watchlist, pulls news
across free sources, and uses Claude to flag what's actually worth a
trading decision — not another headline aggregator.

## Status

**Phase 0 (scaffold), Phase 1 (watchlist CRUD), and Phase 2 (live news
feed) are written**, plus a Settings export/import feature — see
[CLAUDE.md](CLAUDE.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
for the full plan. **Not yet verified**: `gradle/wrapper/gradle-wrapper.jar`
still needs to be generated (open the project in Android Studio, or run
`gradle wrapper` locally) before `./scripts/dev.sh all` can actually build
it — see "Getting started" below. Phases 3–6 (background alerts, Claude
integration, trading depth) haven't started.

## Who it's for

One user: me. Built to sharpen my own stock/options trading by cutting
through news noise, not as a product for distribution.

## What's planned

- **Dashboard** — every tracked ticker as a card with an urgency badge
  (hot/warm/calm), so triage happens before you open anything.
- **Watchlist → Ticker detail** — add/remove tickers; tap one for a
  de-duplicated news feed with sources cited and an AI "why it matters"
  line per story.
- **AI settings** — Claude API key entry (not subscription auth — see
  CLAUDE.md for why), model choice, and a custom "trading lens" prompt.
- **Export / import setup** — back up the watchlist and Claude/Finnhub API
  keys to a JSON file you choose (and restore from one), so reinstalling
  doesn't mean starting over. The exported file contains both keys in plain
  text by design — treat it like a password.
- **Background alerts** — a periodic on-device check that fires a local
  notification when something on your watchlist actually matters, even
  with the app closed. No backend server.

Full phased roadmap, data-source choices, and the architecture reasoning
behind them live in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Getting started

1. Open the project root in Android Studio (recommended — it regenerates
   `gradle/wrapper/gradle-wrapper.jar`, which isn't checked in) **or** run
   `gradle wrapper` locally if you already have Gradle installed.
2. `./scripts/dev.sh all` (bash) or `.\scripts\dev.ps1 all` (PowerShell) —
   runs lint, unit tests, and a debug build. See `scripts/dev.sh -h` for
   individual tasks (`build`, `vet`, `test`, `cov`).
3. Run the `app` configuration from Android Studio, or `./scripts/dev.sh
   build` and install the resulting `dist/market-news-monitor-v<version>-
   build<N>.apk` with `adb install`.

## Disclaimer

A personal information-triage tool, not investment advice. AI urgency
scores are a filter to help you notice things faster, not a signal to
trade on unexamined.
