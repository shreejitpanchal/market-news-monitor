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
Phase 4's previously-deferred dedup clustering is now built too — every
phase on the roadmap is now implemented.
**Builds and launches on-device** (confirmed 2026-09-06), but `./gradlew
test`/`scripts/dev.sh all` still hasn't been run against this code —
don't treat the roadmap checkboxes as "test-suite verified" until that
first real run comes back clean.

## Decisions already made — don't re-litigate these without new information

- **Distribution:** personal sideload only. This is why the Claude API key
  can live in `EncryptedSharedPreferences` on-device instead of behind a
  backend proxy — a public release would need a server so a decompiled
  APK can't leak the key, but a single sideloaded install has no such
  attacker.
- **Platform:** native Kotlin + Jetpack Compose, not Flutter/React Native.
  No iOS target planned, so there's no cross-platform tax worth paying —
  full access to WorkManager, notification channels, and home-screen
  widgets matters more here than portability. This still holds for the
  real app — see the `webapp/` module below, which does not reopen it.
- **`webapp/` + `server/` are a real Chrome desktop client, not a second
  hosted production target.** Added when the user wanted to actually use
  the app locally in Chrome, not just glance at a mockup. `webapp/` is a
  Compose Multiplatform (`wasmJs`) module, additive and isolated from
  `app/` — no shared code, since `app`'s Composables are
  `androidx.compose.*` (Android-only) and Compose Multiplatform is
  `org.jetbrains.compose.*`, different artifacts; `WorkManager`/
  notifications/the Glance widget have no browser equivalent, so
  background alerts are simply dropped — manual refresh only, per
  explicit instruction. `Room`/`Retrofit` don't run on `wasmJs` either,
  so watchlist persistence uses browser `localStorage`
  (`WatchlistStore.kt`) instead of Room.
- **Live data goes through `server/`, a local-only proxy — the browser
  never talks to Finnhub/Alpha Vantage/EDGAR/Claude directly, and never
  holds their API keys.** Calling those APIs straight from a browser tab
  would either hit CORS (SEC EDGAR and Anthropic both plausible blockers)
  or mean the keys sit in browser storage, visible via devtools — a real
  security regression from Android's Keystore, not just extra work.
  `server/` is a plain Kotlin/JVM Ktor app, binds to `127.0.0.1` only,
  and is a **generic credential-injecting relay with no business logic**
  — it forwards a request to the real host and attaches the right
  key/header; all mapping/prompt/parsing logic lives in `webapp/`
  (ported from `app`'s already-working equivalents, e.g.
  `FinnhubClient.kt` ports `FinnhubSource.kt`'s `mapFinnhubNews`). Reads
  its config once at startup from `server/local.properties` (gitignored,
  `server/local.properties.example` is the committed template) — the
  three API keys plus name/email for the EDGAR User-Agent all live
  there, not in the browser; the web Settings screen is **read-only**,
  showing what's configured via `/proxy/status`, not an input form. Two
  simplifications versus the Android app, both to avoid needing a
  date-arithmetic library on the `wasmJs` target: no dedup clustering,
  and date-window/cutoff filtering is either computed server-side (`/proxy/
  finnhub/news`, using `java.time`) or dropped in favor of "take what the
  API already returns" (Alpha Vantage's `outputsize=compact`, EDGAR's
  already-newest-first "recent" list). **This does reintroduce "a
  server," reopening the "no backend server" decision below** — but it's
  local-only, never network-exposed, operated by nobody but you, and
  exists purely so this one browser tab can reach the internet without
  embedding secrets in it. That's a different risk profile than a hosted
  backend, which is what that decision was actually about.
  `scripts/run_web.ps1` (or `.sh`) starts both `server/` and `webapp/`'s
  dev server before opening Chrome — a dedicated launcher alongside
  `dev.sh`/`dev.ps1`, same relationship `build_apk.sh` has to `dev.sh`,
  not a `dev.sh` task, since both are long-running processes rather than
  one-shot gates. **Ports are fixed, not webpack's auto-picked default**:
  proxy on 8787, webapp dev server on 19001 — pinned via
  `webapp/webpack.config.d/devServer.js` (Kotlin/JS's documented webpack-
  override mechanism: every `.js` file there is merged into the generated
  webpack config automatically), not the Kotlin Gradle DSL's
  `KotlinWebpackConfig.DevServer` type directly, since that type's exact
  field mutability isn't worth guessing at in a build script only Gradle
  itself can check. Mirrored in the proxy's CORS allowlist and both
  scripts — an auto-picked port would drift out of sync with whatever URL
  the script or CORS config assumed. The script polls each port until it
  actually accepts a connection before opening Chrome, rather than a
  fixed sleep, since first-run toolchain downloads make a
  short guess unreliable.
- **The webapp dev server is served over HTTPS with a self-signed
  localhost cert**, explicitly requested — not for the proxy, which
  stays plain HTTP (it's `127.0.0.1`-only already; a cert there would
  add a trust step without protecting anything new). `scripts/
  gen_dev_cert.sh` generates `webapp/certs/localhost-{cert,key}.pem`
  (gitignored — not a real secret, but no private key belongs in git;
  both `run_web.ps1`/`.sh` auto-generate it on first run if missing), a
  825-day cert with `subjectAltName=DNS:localhost,IP:127.0.0.1` (modern
  Chrome requires SAN, not just CN). Wired in via `webapp/webpack.config.d/
  devServer.js`'s `server: { type: "https", options: { key, cert } }` —
  falls back to plain HTTP with a console warning if the cert files are
  missing, rather than failing to start. Chrome will flag the cert as
  untrusted on first visit (expected for self-signed — click through).
  The proxy's CORS `allowHost` now allows both `http` and `https`
  schemes for the webapp origin accordingly. The webapp's own calls to
  the proxy stay `http://localhost:8787` even from the HTTPS page —
  Chrome doesn't apply mixed-content blocking to loopback addresses, so
  this isn't a mixed-content violation.
- **AI integration is a Claude API key, not subscription auth.** Reusing a
  Claude.ai Pro/Max login isn't a supported integration path for
  third-party apps — Anthropic doesn't expose that as an API. Don't
  attempt an unofficial session-token workaround; it's fragile and against
  the spirit of the ToS. The key is entered once in Settings and never
  hardcoded, logged, or committed (see `.gitignore`'s `secrets.properties`
  entry).
- **No backend server for the Android app.** Background alerting is
  solved with an on-device `WorkManager` periodic job + local
  notifications, not a push server — see Architecture below for why this
  is sufficient. `server/`'s local-only proxy for the `webapp/` Chrome
  client (above) doesn't reopen this: it's never network-exposed, has no
  accounts, and isn't operated/hosted the way this decision is actually
  about — it exists only so one local browser tab can reach the internet
  without embedding secrets in it.
- **Background polling is user-controlled, not fixed.** The poll interval
  is a Settings value (default 15 min, Android's own floor for periodic
  `WorkManager` work), and background polling can be turned off entirely
  — a `WorkManager` job is enqueued/cancelled from Settings rather than
  scheduled once at install time. Independent of that setting, opening
  the app always triggers an immediate foreground fetch — a stale
  dashboard on open would be a worse experience than the extra API calls
  cost, and this path doesn't touch `WorkManager` at all, so it works
  even with background polling disabled.
- **Settings export/import is a password-encrypted JSON file, not plain
  text.** Originally shipped plaintext by explicit choice (convenience);
  reversed when the user explicitly asked for encryption. `BackupCrypto`
  (`data/backup/BackupCrypto.kt`) derives an AES-256 key from a
  user-entered password via PBKDF2-HMAC-SHA256 (210,000 iterations,
  random salt) and encrypts the *entire* serialized `BackupData` payload
  with AES-GCM (random IV, authenticated) — not just the API-key fields.
  The file on disk is `EncryptedBackupEnvelope`, itself plain JSON
  (format id, iterations, salt/iv/ciphertext as base64), so "the export
  is JSON" still holds even though the payload inside is opaque.
  Deliberately **password-based, not an Android Keystore-bound key**: a
  Keystore key is wiped on uninstall and never leaves the device, which
  would make a backup permanently undecryptable after exactly the
  reinstall/phone-swap scenario export/import exists for. The password
  is asked for at export time (with a confirm field, since a typo makes
  the backup unrecoverable) and again at import time
  (`ExportPasswordDialog`/`ImportPasswordDialog` in `SettingsScreen.kt`)
  — it is never itself stored anywhere. A wrong password or a tampered
  file both fail loudly via GCM's authentication tag
  (`GeneralSecurityException`), surfaced as "incorrect password or
  corrupted file," never silently returning garbage data. There is no
  fallback path for the old plaintext format — this app has no real
  installs yet, so a clean break was preferred over the extra code a
  legacy-format reader would need.
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
- **Add Ticker autocompletes against Finnhub's `/search` endpoint**, not
  a bundled static symbol list — `TickerSymbolSearch` (`data/remote/
  finnhub/TickerSymbolSearch.kt`), wired through `WatchlistViewModel.
  onAddTickerSymbolChange` with a 300ms debounce so each keystroke
  doesn't fire its own call. Same silent-no-op-without-a-key convention
  as the classifier/EDGAR: no Finnhub key set means no suggestions, not
  an error. `mapFinnhubSymbolSearch` drops foreign-exchange listings of
  the same company (symbol contains "."), since the watchlist only
  tracks the primary US-listed ticker, and caps at
  `TickerSymbolSearch.MAX_SUGGESTIONS` (8).
- **Ticker detail's price chart uses Alpha Vantage, not Finnhub** —
  Finnhub's free tier no longer reliably serves historical candle data
  for US stocks (gated behind a paid plan), while Alpha Vantage's free
  tier (25 req/day) was already reserved in `docs/ARCHITECTURE.md`'s
  data-source table for exactly this kind of on-demand call. It's a
  third, separate API key (`SecureSettingsStore.getAlphaVantageApiKey`),
  carried through export/import like the others (`BackupData` version
  4). `AlphaVantagePriceHistoryProvider.getDailyCloses` is loaded once
  per `TickerDetailViewModel` instance (screen open), not on every tap
  of the news-refresh button — daily-granularity price data doesn't
  change intra-day, and the daily cap is tight. Fixed 3-month lookback,
  `outputsize=compact`, no range picker — deliberately minimal scope,
  matching this app's other short time horizons (7-day news lookback,
  24h notification freshness). `PriceChart` is hand-drawn with Compose
  `Canvas` — still no charting library dependency — but is a real chart,
  not just a bare line: Y-axis gridlines + price labels, X-axis date
  labels (first/mid/last point), a filled area under the close line, and
  volume bars (from the same Alpha Vantage response's `"5. volume"`
  field — no extra API call) underneath. Tapping the chart shows a
  crosshair + tooltip for that point's date/price/volume, tracked as
  local `selectedIndex` state reset whenever `points` changes. Same
  silent-empty-result convention as every other optional API
  integration: no key, a rate-limited/invalid-key response (Alpha
  Vantage omits `"Time Series (Daily)"` and returns a `"Note"`/
  `"Information"` string instead), or a network error all mean no chart
  renders — never an error banner.
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
  merged into one card) is built, scoped to a single classification
  batch.** The same Haiku call that assigns urgency also returns an
  optional per-article `cluster` integer; `NewsRepository` turns each
  batch's local integers into a stable `clusterId` string (first article
  id seen for that integer wins, no UUIDs). Deliberately **no
  cross-batch comparison** — a duplicate arriving in a later refresh's
  batch never retroactively merges with one already classified, since
  Claude can only cluster articles it sees together in one call. Beyond
  the ticker-detail merged card, clustering also **collapses
  notifications and the digest**: `collapseClusters()` picks one
  representative article per `clusterId`, used before
  `ArticleNotifier.notifyNewArticles` and before the digest prompt, so a
  multi-outlet story notifies/digests once — but `markNotified` is
  always called with the *full*, uncollapsed list, or an unshown cluster
  sibling would resurface alone once its notified sibling drops off the
  "unnotified" query.
- **Classification is batched per ticker per refresh (one Haiku call for
  every unclassified article), not one call per article.** Cheaper, and
  the natural unit for the clustering pass above, which needs to
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
  back to a generic non-identifying string when the profile is empty —
  `EdgarSource.DEFAULT_USER_AGENT` — now only reachable defensively
  (imports, migrations) since name/email are collected mandatorily below.
  The name/email fields aren't secrets (plain `AppPreferences`, not
  `EncryptedSharedPreferences`) but are still carried through
  export/import like everything else in Settings.
- **Name and email are collected on first launch, before any other
  screen, and are mandatory** — `ui/onboarding/ProfileOnboardingScreen.kt`,
  gated in `MarketNewsMonitorNavHost` via
  `AppPreferences.hasCompletedProfile` (both fields non-blank). Reuses
  the exact same `AppPreferences.userName`/`userEmail` keys Settings'
  "Your info" section already read/wrote — onboarding and Settings are
  two entry points to the same state, not a separate profile store. The
  screen also offers "Import setup instead" (reusing `BackupRepository.
  importFrom`) so restoring a backup with a saved name/email satisfies
  the gate without retyping; an imported file missing either field
  re-shows the form rather than silently passing the gate. Email is
  validated with a plain-Kotlin regex (`isValidProfileEmail` in
  `ProfileValidation.kt`, deliberately its own file so it stays unit-
  testable without pulling in the screen's Compose imports) — format
  only, no verification email is ever sent.
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
- [x] Phase 4 — Claude integration (classification, urgency badges, dedup clustering) (unverified — see Status above)
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

`scripts/build_apk.sh` is a thin convenience wrapper around
`scripts/dev.sh build` for producing a debug APK to sideload manually —
it doesn't reimplement the Gradle invocation, so `scripts/dev.sh`
remains the only place that actually knows how to build this repo.

`scripts/run_web.ps1` / `run_web.sh` start the `server/` proxy and the
`webapp/` dev server, then open Chrome — see the `webapp/`/`server/`
decisions above for what this Chrome client is and isn't. Copy
`server/local.properties.example` to `server/local.properties` and fill
in your keys before running it. Both processes' output is always
captured to `scripts/logs/run_web_proxy.log` and `run_web_webapp.log`
(gitignored) — read those (or paste them back) when something doesn't
come up, since a spawned window's on-screen scrollback isn't always
still there by the time anyone looks. Pass `--debug` (`.sh`) / `-Debug`
(`.ps1`) for `--info --stacktrace` Gradle output when the plain logs
aren't enough.

`gradle/wrapper/gradle-wrapper.jar` is checked in and `./gradlew` works
— it had to be generated via a real local Gradle install (an agent
writing text files can't produce a compiled binary), which also fixed
a duplicate-`shift` bug in the hand-authored `gradlew` that predated
the real jar. If the wrapper ever needs regenerating (e.g. bumping the
Gradle version), that's still a real Gradle install + `gradle wrapper
--gradle-version <version>`, not something to hand-author again.

**Needs a local Android SDK too** — a machine-specific `local.properties`
with `sdk.dir=<path to Android SDK>` (or an `ANDROID_HOME` env var),
never committed (`.gitignore` already covers `local.properties`). On
this dev machine that's `C:\Users\shree\Android\sdk` — installed for
the `coding-adventure` Flutter project, but it already had SDK platform
35 + build-tools 35.0.0 + accepted licenses, exactly what this project
needs, so it's reused rather than installing a second one.
