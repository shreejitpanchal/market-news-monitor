# CLAUDE.md

This file provides guidance to Claude Code when working with code in this
repository.

## What this is

**MarketNewsMonitor** — a personal, single-user Android app that turns
watchlist news into trading signal instead of noise. Not a product for
distribution: sideloaded on one phone, no Play Store release, no accounts,
no other users. Every architectural choice below optimizes for "cheapest
thing that actually works for one person" over "correct for a public app."

**Current status: Phase 0 (scaffold) through Phase 4 (Claude
classification) are written, and Phase 5 ("trading-specific depth") is
now fully built** (SEC filings, earnings-aware sensitivity, pre-market
digest, home-screen widget — see Roadmap below), plus a Settings
export/import feature added ahead of its normal schedule because
export/import needed somewhere to store the Claude and Finnhub API keys.
Only Phase 4's deferred dedup clustering remains anywhere on the roadmap.
**Still not build-verified** — `gradle/wrapper/gradle-wrapper.jar` is
now checked in (generated 2026-09-06 via a local Gradle 8.9 install) and
`./gradlew -v` runs, but no actual `./gradlew test`/`assembleDebug` (via
`scripts/dev.sh all`) has been run against this code yet. Don't treat
the roadmap checkboxes as "tested and working" until that first real
build/test run comes back clean.

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
- **Dedup clustering (near-duplicate stories from different outlets
  merged into one card) is deferred, not built.** Phase 4 shipped urgency
  + "why it matters" only — Finnhub/Google News RSS already dedup by
  exact URL, so cross-outlet duplicates just show as separate cards for
  now. Clustering needs its own schema (a grouping) and merged-card UI;
  don't assume it exists just because classification does.
- **Classification is batched per ticker per refresh (one Haiku call for
  every unclassified article), not one call per article.** Cheaper, and
  the natural unit for the deferred clustering pass above, which needs to
  see articles together anyway. Capped at
  `ClaudeArticleClassifier.MAX_ARTICLES_PER_CALL` (20) per call; a bigger
  backlog is picked up over subsequent polls rather than one unbounded
  prompt. A missing/invalid key, or a malformed/failed response, is a
  silent no-op — articles stay unclassified and are retried next refresh,
  never a crash.
- **SEC EDGAR's required User-Agent is built from a Settings "Your info"
  profile (name/email), never hardcoded in source.** SEC's fair-access
  policy requires every request identify a real requester; committing a
  personal email into source (and every outbound request) was rejected —
  see `data/remote/edgar/EdgarSource.kt`'s `buildEdgarUserAgent`. Falls
  back to a generic non-identifying string when the profile is empty, so
  EDGAR calls degrade rather than fail before first Settings setup. The
  name/email fields aren't secrets (plain `AppPreferences`, not
  `EncryptedSharedPreferences`) but are still carried through
  export/import like everything else in Settings.
- **EDGAR ticker→CIK mapping is cached in memory for the process
  lifetime, not re-fetched per poll** — it's the whole market (~1MB) and
  changes rarely. A failed fetch is deliberately *not* cached, so the
  next call retries instead of permanently disabling EDGAR until restart.
- **Notifications are gated on classified urgency, not just "new and
  fresh."** Baseline: only `hot` articles notify (`NewsPollRunner.
  BASELINE_NOTIFY_URGENCIES`) — this was a real gap found while planning
  Phase 5b: `docs/ARCHITECTURE.md` always said the worker should fire "for
  anything flagged urgent," but that gate was never wired up when Phase 4
  added classification, so every fresh article was notifying regardless
  of urgency. An unclassified article (`urgency == null`) never notifies;
  it's retried by the existing unclassified-retry loop and can still
  notify on a later poll while inside the freshness window.
- **Within ±1 day of a ticker's earnings date, `warm` also notifies**
  (`NewsPollRunner.NEAR_EARNINGS_NOTIFY_URGENCIES`), via a Finnhub
  earnings-calendar check (`FinnhubEarningsCalendarProvider`, same key as
  company news, no caching — it's one cheap per-ticker call per poll).
  Fails closed to the stricter baseline on any error, never open. Don't
  change the ±1 day window or the hot/warm split without deciding it's
  worth the same deliberation as the freshness window above.
- **The pre-market digest is fixed at 8:00 AM device-local time, no
  Settings time picker.** Covers only hot/warm articles from the last
  24h across the whole (unmuted) watchlist — same urgency philosophy as
  everywhere else — and is silently skipped (no Sonnet call, no
  notification) on a day with nothing notable, rather than sending an
  empty digest. Off by default (`AppPreferences.digestEnabled`); a new
  unsolicited daily notification shouldn't be forced on everyone.
- **This is the only place the app calls Claude Sonnet** (`ClaudeApi.
  MODEL_SONNET`) — everything else uses Haiku. Don't switch the digest to
  Haiku or the per-article classifier to Sonnet without revisiting the
  cost-split reasoning in Architecture below.
- **Two `WorkManager` jobs, two `WorkerFactory`s, combined with
  `DelegatingWorkerFactory`** (`MarketNewsMonitorApp.
  getWorkManagerConfiguration`) rather than one factory branching on
  worker type — adding a third scheduled job later means adding a third
  factory, not editing the existing two.
- **The home-screen widget is built with Jetpack Glance**
  (`androidx.glance:glance-appwidget`), not the older RemoteViews/
  `AppWidgetProvider` API directly — a natural fit since the app is
  already all-Compose. `MarketNewsMonitorWidgetReceiver` is the actual
  `AppWidgetProvider` the OS binds to; `MarketNewsMonitorWidget` is the
  Glance composable.
- **Widget rows deep-link straight to that ticker's detail screen — a
  deliberate exception to the no-deep-link notification decision above.**
  A widget tap always finishes-and-recreates `MainActivity` (standard
  launch mode + `FLAG_ACTIVITY_CLEAR_TOP`), so `onCreate` reliably sees
  the tapped ticker via `intent.getStringExtra(MainActivity.
  EXTRA_TICKER_SYMBOL)` with none of the re-entrant-Activity/`onNewIntent`
  concerns Phase 3 was avoiding for notifications. That decision for
  notifications stands unless revisited separately — don't assume this
  widget mechanism was meant to change it.
- **The widget stays fresh via one hook, not one per call site:**
  `NewsRepository.refresh()` calls `WidgetUpdater.requestUpdate()` at the
  end, unconditionally — every refresh path (manual ticker-detail,
  background poll) updates it automatically. The `widgetUpdater`
  constructor parameter defaults to `WidgetUpdater.Noop` specifically so
  the dozen-plus existing test call sites didn't all need updating —
  don't remove that default without checking what still relies on it.
  `updatePeriodMillis` in the widget's provider XML is only a 30-minute
  safety net for when background polling is off, same "acceptable drift"
  reasoning as the digest scheduler — the `refresh()` hook is the real
  mechanism.
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
  `GoogleNewsRssSource`, `EdgarSource`) behind the
  `NewsSource` interface, looked up from `NewsSourceRegistry` the way
  `coding-adventure`'s `app/execution/registry.py` looks up one
  `ExecutionEngine` per language. Adding a data source later (a paid
  feed, options flow) means adding one class, not branching inside a
  shared fetcher.
- **WorkManager** runs two independent periodic jobs, each its own
  on/off Settings toggle — neither is fixed/always-on: `NewsPollWorker`
  fetches new articles for watchlisted tickers, diffs against Room,
  classifies only the *new* ones with Claude Haiku, and fires a local
  notification for anything flagged urgent (interval also Settings-
  controlled); `DigestWorker` runs once daily at a fixed time, summarizing
  the whole watchlist's notable news with Claude Sonnet.
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
- [x] Phase 4 — Claude integration (classification, urgency badges) (unverified — see Status above; dedup clustering deferred)
- [x] Phase 5 — trading-specific depth (filings, earnings-aware alerts, widget) (unverified — see Status above)
  - [x] SEC filings feed (EDGAR 8-K/Form 4 via the existing news registry) (unverified — see Status above)
  - [x] Earnings-calendar-aware alert sensitivity (unverified — see Status above; also added the missing baseline urgency gate)
  - [x] Pre-market digest notification (Claude Sonnet) (unverified — see Status above; fixed 8am, hot/warm-only, off by default)
  - [x] Home-screen widget (unverified — see Status above; Jetpack Glance, deep-links per row)

## Commands

`scripts/dev.sh` (bash) / `scripts/dev.ps1` (PowerShell) are the single
source of truth for build/lint/test — see `.claude/commands/verify.md`.
Key tasks: `build` (`./gradlew assembleDebug`, copies the APK to `dist/`),
`vet` (`./gradlew lint`), `test` (`./gradlew test`), `cov` (Jacoco),
`all` = build+vet+test, `full` = all+cov+graphify.

`build_apk.sh` (repo root) is a thin convenience wrapper around
`scripts/dev.sh build` for producing a debug APK to sideload manually —
it doesn't reimplement the Gradle invocation, so `scripts/dev.sh`
remains the only place that actually knows how to build this repo.

`gradle/wrapper/gradle-wrapper.jar` is checked in and `./gradlew` works
— it had to be generated via a real local Gradle install (an agent
writing text files can't produce a compiled binary), which also fixed
a duplicate-`shift` bug in the hand-authored `gradlew` that predated
the real jar. If the wrapper ever needs regenerating (e.g. bumping the
Gradle version), that's still a real Gradle install + `gradle wrapper
--gradle-version <version>`, not something to hand-author again.
