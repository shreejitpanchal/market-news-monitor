# MarketNewsMonitor

A personal Android app that watches a stock/options watchlist, pulls news
across free sources, and uses Claude to flag what's actually worth a
trading decision — not another headline aggregator.

## Who it's for

One user: me. Built to sharpen my own stock/options trading by cutting
through news noise, not as a product for distribution.

## Features

- **Dashboard** — every tracked ticker as a card with an urgency badge
  (hot/warm/calm), so triage happens before you open anything.
- **Watchlist → Ticker detail** — add/remove tickers, with symbol/company
  name suggestions as you type; tap one for a 3-month daily price chart
  and a de-duplicated news feed, pulled from Finnhub, Google News, and
  SEC EDGAR filings, with sources cited and an AI "why it matters" line
  per story.
- **AI classification** — Claude Haiku scores each new article's
  urgency and writes a one-line "why it matters," so the badges and
  feed reflect what's actually worth attention, not just what's new.
- **API key settings** — Claude, Finnhub, and Alpha Vantage key entry,
  all free-tier (not subscription auth for Claude — see CLAUDE.md for
  why).
- **Background alerts** — a periodic on-device check that fires a local
  notification when something on your watchlist actually matters, even
  with the app closed. No backend server. Notifications sharpen near a
  ticker's earnings date, when more news is likely to matter.
- **Pre-market digest** — an optional once-a-day, 8 AM summary of your
  whole watchlist's notable news, written by Claude Sonnet. Off by
  default; skipped on days with nothing worth summarizing.
- **Home-screen widget** — the same ticker + urgency badge list as
  Dashboard, right on your home screen; tap a ticker to jump straight
  into its news feed.
- **Export / import setup** — back up the watchlist and API keys to a
  JSON file you choose (and restore from one), so reinstalling doesn't
  mean starting over. The exported file contains your keys in plain
  text by design — treat it like a password.

Full architecture reasoning and data-source choices live in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Disclaimer

A personal information-triage tool, not investment advice. AI urgency
scores are a filter to help you notice things faster, not a signal to
trade on unexamined.

On first launch, the app asks for your name and email (also editable
later under Settings → "Your info"). That information is sent in plain
text to SEC EDGAR's servers with every filings request — required by
their fair-access policy so requests are traceable to a real requester.
It is not sent to any other data source this app uses (Finnhub, Google
News RSS, or the Claude API).
