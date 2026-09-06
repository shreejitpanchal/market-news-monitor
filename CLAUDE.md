# CLAUDE.md

This file provides guidance to Claude Code when working with code in this
repository.

## What this is

**MarketNewsMonitor** — a personal, single-user Android app that turns
watchlist news into trading signal instead of noise. Not a product for
distribution: sideloaded on one phone, no Play Store release, no accounts,
no other users. Every architectural choice below optimizes for "cheapest
thing that actually works for one person" over "correct for a public app."

**Current status: Phase 0 (scaffold) through Phase 3 (background alerts)
are written**, plus a Settings export/import feature added ahead of its
normal Phase 5 slot (see Roadmap below) because export/import needed
somewhere to store the Claude and Finnhub API keys. **Not yet verified** —
`gradle/wrapper/gradle-wrapper.jar` isn't checked in (see Commands below),
so no `./gradlew` command has actually been run against this code yet.
Don't treat the roadmap checkboxes as "tested and working" until that
first build/test run comes back clean.

## Decisions already made — don't re-litigate these without new information

- **Distribution:** personal sideload only. This is why the Claude API key
  can live in `EncryptedSharedPreferences` on-device instead of behind a
  backend proxy — a public release would need a server so a decompiled
  APK can't leak the key, but a single sideloaded install has no such
  attacker.
- **Platform:** native Kotlin + Jetpack Compose, not Flutter/React Native.
  No iOS target planned, so there's no cross-platform tax worth paying —
  full access to WorkManager, notification channels, and home-screen
  widgets matters more here than portability.
- **AI integration is a Claude API key, not subscription auth.** Reusing a
  Claude.ai Pro/Max login isn't a supported integration path for
  third-party apps — Anthropic doesn't expose that as an API. Don't
  attempt an unofficial session-token workaround; it's fragile and against
  the spirit of the ToS. The key is entered once in Settings and never
  hardcoded, logged, or committed (see `.gitignore`'s `secrets.properties`
  entry).
- **No backend server.** Background alerting is solved with an on-device
  `WorkManager` periodic job + local notifications, not a push server —
  see Architecture below for why this is sufficient.
- **Background polling is user-controlled, not fixed.** The poll interval
  is a Settings value (default 15 min, Android's own floor for periodic
  `WorkManager` work), and background polling can be turned off entirely
  — a `WorkManager` job is enqueued/cancelled from Settings rather than
  scheduled once at install time. Independent of that setting, opening
  the app always triggers an immediate foreground fetch — a stale
  dashboard on open would be a worse experience than the extra API calls
  cost, and this path doesn't touch `WorkManager` at all, so it works
  even with background polling disabled.
- **Settings export/import includes the Claude and Finnhub API keys in
  plain text.** User's explicit choice, made when this feature was built
  (Phase 1) — convenience over encrypting the export. The Settings screen
  shows a warning above the Export button rather than hiding the risk.
  Don't add export-file encryption unasked; don't silently drop a key from
  exports either — both would contradict a decision already made.
- **RSS source is Google News per-ticker search, not Yahoo
  Finance/Reuters/MarketWatch as originally documented.** Changed during
  Phase 2 — those three no longer reliably serve public per-ticker RSS
  (Reuters has none at all). See `docs/ARCHITECTURE.md`'s data-source
  table for the replacement URL and rationale. Don't revert to the
  original three without first confirming they actually work again.
- **Finnhub needs its own API key**, separate from Claude's — added to
  Settings/export-import in Phase 2 alongside the news-fetching code that
  needs it, ahead of its implied Phase 5 slot, same reasoning as the
  Claude key/export-import pairing above.
- **Background-poll notifications are grouped one-per-ticker, not
  one-per-article**, and tapping one opens the app to Dashboard rather
  than deep-linking to the specific ticker. Both were explicit scope
  choices in Phase 3 (avoid notification spam; avoid Activity/intent
  deep-link edge cases in a first pass) — revisit deliberately, don't
  "improve" silently.
- **Notification eligibility is a 24-hour freshness window on
  `publishedAt`, not just the `notified` flag.** Without it, a freshly
  added ticker's first background poll would dump a week of Finnhub
  backfill as one giant notification. Backfill articles still get
  inserted and are visible in ticker detail — they just never notify.
  See `NewsPollRunner.NOTIFICATION_FRESHNESS_WINDOW_MILLIS`.
- **Data budget: free-tier APIs only**, chosen for *latency*, not just
  cost — NewsAPI's free tier has a 24-hour article delay and is
  explicitly excluded from the alerting path for that reason (see
  `docs/ARCHITECTURE.md`'s data-source table). Don't add a paid feed
  without confirming the free-tier limit actually bit first.

## Architecture (planned)

Full reasoning, the data-source comparison table, and the phased roadmap
live in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — read that before
making a structural change. Summary:

- **Kotlin + Jetpack Compose**, MVVM-ish: Compose screens observe
  `StateFlow` exposed by a ViewModel per screen, ViewModels call
  repository classes, repositories own the Retrofit clients + Room DAOs.
- **Room** is the source of truth for tickers, cached articles, and
  "already notified" state — the UI never talks to the network layer
  directly.
- **News sources are a small engine/registry pattern**, not one
  monolithic fetcher — one class per source (`FinnhubSource`,
  `GoogleNewsRssSource`, and eventually `EdgarSource`) behind the
  `NewsSource` interface, looked up from `NewsSourceRegistry` the way
  `coding-adventure`'s `app/execution/registry.py` looks up one
  `ExecutionEngine` per language. Adding a data source later (a paid
  feed, options flow) means adding one class, not branching inside a
  shared fetcher.
- **WorkManager** runs a periodic worker, fetching new articles for
  watchlisted tickers, diffing against Room, classifying only the *new*
  ones with Claude Haiku, and firing a local notification for anything
  flagged urgent. Both the interval and whether this job runs at all are
  user-controlled from Settings (see above) — this is not a fixed,
  always-on job.
- **Opening the app always does a foreground fetch** regardless of the
  background-polling setting, so the dashboard is never stale just
  because the user turned background polling off.
- **Claude Haiku** for cheap per-article classification (urgency +
  one-line "why it matters" + dedup clustering); **Claude Sonnet** only
  for the once-a-day digest — this split is a cost decision, not
  arbitrary, since Haiku runs on every new article and Sonnet runs once a
  day.

## Working with Claude Code on this repo — practices to follow

- **Don't invent tooling that isn't configured.** If there's no lint/
  format config in the repo at the time you're reading this, don't add
  one unasked, and don't assume ktlint/detekt exists until you've checked.
- **Secrets never enter git.** The Claude API key and any other credential
  live in `EncryptedSharedPreferences` on-device, never in source, a
  committed `local.properties`, or a hardcoded string — check `git diff`
  for anything that looks like a key before committing.
- **This app makes real trading-adjacent decisions for a real person.**
  Prefer surfacing uncertainty (a low-confidence AI tag, a stale-data
  indicator) over silently guessing. A wrong "quiet" badge on a ticker
  that actually moved is worse than an over-eager "urgent" one.
- **Investigate root causes, don't skip failing tests** or loosen an
  assertion to make it pass once a test suite exists.
- **Content vs. code:** if the watchlist/ticker-metadata format grows
  complex enough to want static seed data, prefer a data file (JSON/YAML
  bundled as an asset) over hardcoding it into Kotlin, matching the
  content-is-data lesson from this project's sibling repo,
  [coding-adventure](https://github.com/shreejitpanchal/coding-adventure).
- **Verify before claiming done.** Once the Gradle project exists, use
  `.claude/commands/verify.md` (build + test) before reporting a change
  complete — don't rely on "it should compile."

## Roadmap status

See `docs/ARCHITECTURE.md` for the full phase list. Update the checkbox
here as each phase actually lands:

- [x] Phase 0 — project scaffold (unverified — see Status above)
- [x] Phase 1 — watchlist CRUD, no live news yet (unverified — see Status above)
- [x] Phase 2 — live news feed (Finnhub + Google News RSS) (unverified — see Status above)
- [x] Phase 3 — background alerts (WorkManager + notifications) (unverified — see Status above)
- [ ] Phase 4 — Claude integration (classification, urgency badges)
- [ ] Phase 5 — trading-specific depth (filings, earnings-aware alerts, widget)

## Commands

`scripts/dev.sh` (bash) / `scripts/dev.ps1` (PowerShell) are the single
source of truth for build/lint/test — see `.claude/commands/verify.md`.
Key tasks: `build` (`./gradlew assembleDebug`, copies the APK to `dist/`),
`vet` (`./gradlew lint`), `test` (`./gradlew test`), `cov` (Jacoco),
`all` = build+vet+test, `full` = all+cov+graphify.

**Before any of this works**, `gradle/wrapper/gradle-wrapper.jar` has to
exist — it's a compiled binary that can't be checked in by an agent
writing text files, so it isn't committed yet. Open the project in Android
Studio once (regenerates it on sync) or run `gradle wrapper` locally.
