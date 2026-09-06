# MarketNewsMonitor

A personal Android app that watches a stock/options watchlist, pulls news
across free sources, and uses Claude to flag what's actually worth a
trading decision — not another headline aggregator.

## Status

**Planning stage.** No app code yet — see [CLAUDE.md](CLAUDE.md) and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the locked-in plan.
Phase 0 (project scaffold) hasn't started.

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
- **Background alerts** — a periodic on-device check that fires a local
  notification when something on your watchlist actually matters, even
  with the app closed. No backend server.

Full phased roadmap, data-source choices, and the architecture reasoning
behind them live in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Getting started

Not yet runnable — Phase 0 will add the Android Studio / Gradle project
scaffold and this section will get real build/run commands at that point.

## Disclaimer

A personal information-triage tool, not investment advice. AI urgency
scores are a filter to help you notice things faster, not a signal to
trade on unexamined.
