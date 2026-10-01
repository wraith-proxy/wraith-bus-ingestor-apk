# Roadmap

Where this is now, and what's next. Not a schedule -- just the order
things need to happen in, revised as the plan changes.

Milestone numbers (`1.5.0` onward) continue
[wraith-bus-ingestor](https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor)'s
own `docs/ROADMAP.md` numbering, not restarted at `1.0.0` -- this repo
*is* that project's Android app, split out on milestone 1.8.0, not a
new project. `versionCode`/`versionName` in `app/build.gradle.kts` are
this repo's own version, independent of these milestone numbers and of
the platform repo's `pyproject.toml` version.

## 1.5.0 -- Android APK, LineageOS Tegu (Pixel 9a), Android 16+

Landed 2026-09-16 (while still inside `wraith-bus-ingestor`, before the
1.8.0 split -- paths below are given as they are now, post-split).
Full native, not a WebView wrapper -- Jetpack Compose + OkHttp,
single-device scope (Tegu only, minSdk 36), two screens plus push.

- **Tracking.** `ui/tracking/TrackingScreen.kt` -- a MapLibre map using
  the exact same tile source and style as `ghost-site`'s web bus map
  (`assets/style.json`, translated from that page's own style object, not
  a second cartography invented for the phone), live bus positions from
  `/buses` refreshed every 60s (matching the web map's own cadence),
  tap-to-query `/bus/<id>` in a bottom sheet, and a "Show route" action
  that draws `/route`'s line+stops overlay. A debounced search bar hits
  `/search` and recenters the camera. Zero new backend work, same
  endpoints the web map already uses.
- **Dashboard.** `ui/dashboard/DashboardScreen.kt` does NOT query QuestDB
  or Grafana directly from the phone -- it hits two new read-only Flask
  endpoints instead:
  - `/api/metrics/summary` -- feed age, buses tracked, positions/hour,
    hours since build, DQ issues. Each field is its own independent
    `qdb_exec` call and fails on its own, the same "one bad query
    shouldn't blank the others" reasoning `bus_detail()` already applies.
  - `/api/metrics/punctuality` -- system on-time % and median delay, ETA
    model fit, and the worst-lines table, straight off
    `line_punctuality_summary` and `eta_model_summary`, the exact tables
    and queries the Grafana dashboard's own "Punctuality & ETA models"
    row already uses.

  One API surface for every client (web, Grafana, phone); QuestDB stays
  off the public internet regardless of how Grafana ends up exposed.
  Both endpoints verified against live QuestDB with real data before any
  Kotlin was written (25,910 buses, 37.3% system on-time, line 320 the
  worst offender), and covered by two tests each: the degrade-quietly
  path (matches CI, where `QUESTDB_URL` is unset) and the real success
  path against a faked QuestDB response.
- **Push notifications.** `ntfy/NtfyForegroundService.kt` -- a foreground
  service holding a live OkHttp WebSocket to the same `ntfy` topic
  Grafana's alerts already publish to, posting a real Android
  notification per message. Doubling backoff on disconnect (2s -> 60s),
  the same shape the platform repo's own `ingestor/ingest.py` dead-feed
  backoff uses, just tuned faster since this is a live push channel, not
  a slow poll. The topic is a `BuildConfig` field sourced from
  `local.properties` (gitignored, `local.properties.example` committed
  as the template) -- never hardcoded into a committed `.kt` file.
- **Built via ultracode, verified by a real compile, not just review.**
  A full Android SDK (platform 36, build-tools) and Gradle toolchain were
  set up specifically to compile this for real. Two real bugs only a
  compiler catches were found and fixed this way: the agent-written
  `TrackingScreen.kt` imported GeoJSON types from `com.mapbox.geojson`
  (MapLibre's historical upstream) when 11.x actually repackaged them
  under `org.maplibre.geojson` -- confirmed by pulling the real
  `android-sdk-geojson` jar and listing its classes, not guessed twice.
  Every hand-written XML comment that used `--` (this project's own
  house style) had to be found and fixed, since XML forbids it in
  comments. `./gradlew clean assembleDebug` succeeds from a clean
  checkout: real APK, `com.thomaspeoples.wraithbus`, launchable
  `MainActivity`, `minSdk = targetSdk = compileSdk = 36`.
- **Dependency versions are pinned one wave back from bleeding-edge
  androidx, deliberately.** The latest `core-ktx`/Compose/`lifecycle`/
  `navigation-compose` releases as of this build all require compiling
  against API 37 -- which is still an active preview cycle on Google's
  SDK repository (`android-37.0`/`.1`/`.2`, no plain stable `37` yet),
  not the real, stable, shipping Android version this app's "Android
  16+ minSdk" actually means. Every dependency was rolled back one
  release to the last wave that targets stable API 36 instead of
  chasing a preview platform for a single-device app that ships once.

**Not built:** a signed release variant. This is a sideloaded,
single-device app with no store distribution -- the debug build is
already a real, installable APK, and there is nothing a release
signing config would buy here beyond ceremony.

**Shipped, then found broken on the real device, twice -- both fixed
same-day:**

- **The dashboard showed "No data" everywhere.** `/api/metrics/summary`
  404'd in production. Merging the app's feature branch to the platform
  repo's `main` never deploys `api` -- nothing in its
  `.gitea/workflows/` rebuilds it, a gap that had simply never been hit
  before because nothing had changed `backend/app.py` and needed
  shipping on its own since the split from `ghost-site`. Fixed by
  rebuilding and recreating `ghost-backend` by hand and reloading
  `ghost-web`'s nginx; verified against the real public URL afterward,
  not just "container came up healthy." Full writeup: platform repo's
  [ADR-0004](https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor/src/branch/main/docs/adr/0004-api-container-has-no-deploy-automation.md).
- **The map showed no buses.** Not a missing-data bug -- `/buses`
  returned 18,001 real features. 77% of them carry `status: "unknown"`
  (confirmed against the live feed), and `TrackingScreen.kt`'s
  `StatusUnknownColor` (`#6D7787`, correctly copied from the web map's
  own palette) is near-identical to `style.json`'s own `roads-major`
  color (`#6b7889`) -- most dots were camouflaged against major roads,
  not absent. The web map's own palette already has a `busStroke`
  (`#12161c`) that the Kotlin translation simply never carried over.
  Fixed by adding the missing stroke (verified against the real
  `PropertyFactory`/`CircleLayer` API by disassembling the compiled
  MapLibre jar with `javap`, not assumed) and bumping the circle radius
  slightly. `setGeoJson()` vs. `setGeoJsonSync()` was investigated as a
  second possible cause -- confirmed real (the jar exports both, with
  different threading semantics) but left as async on purpose: the feed
  runs to ~18k features GB-wide, and parsing that synchronously on the
  main thread every 60s would trade a visibility bug for a jank one.

**Logo redesigned.** The first cut (`ic_launcher_foreground.xml`) was a
bare orange rectangle with two dots for wheels -- functional, not a real
icon. Three independent design directions were generated (a realistic
coach silhouette, a bold geometric bus-front mark, a map-pin-with-bus
glyph), each rendered to PNG at both full size and actual 48px launcher
size with `resvg` and inspected before picking one -- the map-pin
candidate lost specifically at 48px, where its interior bus glyph turned
to mud. The coach silhouette won: most recognizable, holds up small,
fits the brand's palette. The shipped XML was verified by re-extracting
its own `pathData` values into a throwaway SVG and re-rendering *that*,
so what was inspected is provably what ships, not a hand-typed
approximation of it.

**About, FAQ, and an in-app update notice.** Reached from a new
overflow menu in a `TopAppBar` (`AppNav.kt`), keeping the primary bottom
nav to Tracking/Dashboard rather than crowding four tabs onto a phone
screen for two low-frequency destinations. About covers what the app is,
BODS/OpenStreetMap attribution (the OSM credit line is a real license
requirement of using that data, not decoration), the MIT license and
source link, and a plain privacy statement. FAQ covers 12 real questions
grounded in the app's actual behaviour -- why most buses show `unknown`
(roughly three-quarters, confirmed against the live feed, not a guess),
why there's no way to repoint the app at a different server, what the
push notifications actually are. `update/UpdateChecker.kt` checks
Releases for a newer `android-X.Y.Z-debug` tag once per cold start and
shows a dialog if one exists -- a courtesy for someone who sideloaded
directly, since Obtainium and F-Droid already watch the same Releases
page on their own without the app's help.

**F-Droid and Obtainium readiness.** Full writeup:
[docs/android-distribution.md](android-distribution.md). The short
version: Obtainium already works today (no code changes needed -- it was
already shipping real releases in exactly the shape Obtainium expects).
F-Droid is split into two different things, and only one was in scope
here: the *public* F-Droid catalog needs a merge request against a
third-party project (`fdroiddata`) that only the app owner should decide
to open, so that was **not done**; what *was* done is everything the app
itself needs to be F-Droid-buildable --
`fastlane/metadata/android/en-US/` (title, descriptions,
per-`versionCode` changelogs, a 512×512 store icon rendered from the
same vector artwork as the launcher icon), a real reproducible-build
story (`local.properties` absent -- exactly F-Droid's build-server
state -- degrades to safe defaults, not a broken build), no proprietary
dependencies, and `versionCode`/`versionName` promoted to the actual
source of truth for the app's version rather than the release-tag names.
That last change surfaced a real inconsistency: `versionCode` had never
actually been bumped for the `1.0.0` -> `1.0.1` re-ship, both silently
carrying `versionCode 1` -- fixed by setting this release to the true
next value, `2`, rather than inventing a `3` to match the tag-name count.

**Also removed:** `ACCESS_COARSE_LOCATION`/`ACCESS_FINE_LOCATION`,
merged in transitively by MapLibre's own manifest for a "show my
position" feature this app never enables. Stripped via
`tools:node="remove"` rather than carried silently -- an app requesting
location it doesn't use is exactly what an F-Droid reviewer would flag,
so it's fixed rather than left for one to catch.

**Shipped, then found broken on the real device again, once -- fixed
same-day:**

- **Route line was nearly invisible, on both the phone and the web map.**
  `TrackingScreen.kt`'s `RouteLineColor` (`#9AA4B2`) was copied correctly
  from `ghost-site`'s own `MAP_PALETTE.dark.route` -- but that value was
  itself wrong: identical to the palette's `sibStroke` and close enough
  in lightness to `style.json`'s `roads-major` (`#6b7889`) to disappear
  into ordinary roads at any zoom. Not a translation bug this time, an
  upstream one. Fixed in both places: `MAP_PALETTE` (`bus-map/index.html`
  in `ghost-site`, a separate repo) moved to a saturated teal/cyan no
  other layer uses (`#0f7a91` light, `#4fd7e8` dark), the route line's
  width and opacity both went up, and the Kotlin copy was updated to
  match the dark value so phone and browser read the same route color
  again.
- **Tapping a bus required an extra tap to see its route, and showed raw
  ISO timestamps.** `BusDetailSheetContent` had a "Show route" button
  that fetched and drew `/route` only after the sheet was already open --
  backwards from what's actually useful, since the sheet then covers the
  bottom half of the map the route just appeared on. Reworked into one
  `selectBus()` path (shared by a map tap and a bus-number search hit,
  see below): fetch the bus, fetch and draw its route, fit the camera to
  the route's bounds, *then* open the sheet. The button is gone -- there's
  nothing left for it to do. ETA/predicted-arrival/next-stop-ETA fields
  now format through `isoToLocalTime()` (`java.time`, no desugaring
  needed at `minSdk 36`) instead of printing the raw
  `2026-09-16T18:57:39Z` the API returns.
- **Search only ever matched localities.** `/search`'s `lines`/`bus_ids`
  fields were already computed server-side (in the platform repo's
  `backend/app.py`) and already parsed into `SearchResult` client-side --
  just never rendered. A query like "X93" matches no locality at all, so
  the existing locality-driven line lookup (`marts['line_loc']`) never
  fired for it either; searching a route number returned nothing.
  Backend now also matches live line codes directly against the query
  (`/search`, independent of any locality match), and the search results
  card renders `lines`/`bus_ids` above the locality matches -- tapping a
  line fetches and fits its route, tapping a bus number runs the same
  `selectBus()` path a map tap does. Covered by
  `test_search_matches_line_number_directly` in the platform repo.
- **From About or FAQ, the bottom nav's Dashboard/Tracking icons did
  nothing.** `navigateToTab()`'s `popUpTo(startDestination){ saveState =
  true }` / `restoreState = true` pairing is the right pattern for
  several persistent, parallel tab stacks -- this app has one, with
  About/FAQ sitting two hops above it rather than beside it, so a stale
  saved back-stack entry could swallow the tap. Bottom-bar taps now hard
  reset to a single entry (`popUpTo(startDestination){ inclusive = true
  }`, no saved state) regardless of where they're tapped from -- can't
  get stuck the same way. Overflow-menu taps to About/FAQ stayed a plain
  push (`navigateToSecondary()`, `launchSingleTop` only, no `popUpTo`):
  resetting those too would have "fixed" the bug by breaking the system
  Back button instead, since with nothing left below About on the stack,
  Back would exit the app rather than return to Tracking or Dashboard.

**Where the bus data comes from, for the record (asked while chasing
the above):** live vehicle positions are BODS's SIRI-VM feed, polled
every 60s -- there is no static timetable/GTFS/TransXChange ingest at
all. Stop sequences and route geometry are inferred from where live
buses have actually been seen, matched to the nearest real stop in the
NaPTAN register (the platform repo's `dbt/models/staging/naptan/`, the
same 435k-row GB-wide register every UK transport app draws stop
identity from) and snapped to roads via a self-hosted OSRM. That's a
different approach from timetable-first apps like
[billthefarmer/buses](https://github.com/billthefarmer/buses) (which
reads BODS's separate TransXChange timetable dataset directly) -- this
app's routes are "what buses actually did," not "what the timetable
says they should do," which is also why a route can come back truncated
or `note`d as merged-suspect for a thin-data line. See the platform
repo's `docs/ROADMAP.md` ("Considered, not started -- static timetable
ingest") for the fuller tradeoff if this ever gets revisited.

## 1.6.0 -- F-Droid public submission

Not started. `docs/android-distribution.md` (landed in 1.5.0) already
covers everything the *app itself* needs to be F-Droid-buildable --
fastlane metadata, a reproducible build with `local.properties` absent,
no proprietary dependencies, `versionCode`/`versionName` as the real
source of truth. This milestone is the other half: actually submitting
to the public catalog, per F-Droid's own
[Submitting to F-Droid quick start](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

**What is and isn't the blocker, stated plainly.** Self-hosting the
backend is not the problem -- that's the whole premise of this project,
and it's exactly what Obtainium + the self-hosted F-Droid repo (both
already shipped in 1.5.0) already deliver: a free, real app, sideloaded
or installed via F-Droid-the-client, hitting one person's own server.
Nothing about that requires a listing in F-Droid's *public, centrally
searchable* catalog (`fdroiddata`/`f-droid.org`) -- that catalog is
specifically for apps a stranger finds by searching and expects to be
general-purpose. The API base URL is baked in at build time
(`BuildConfig.API_BASE_URL`, defaulting to
`https://www.thomaspeoples.com/api/`) and not repointable
(`ui/faq/FaqScreen.kt`: "Can I point this app at a different server?
No."), so a stranger who found this in the public catalog would get a
permanent client for someone else's bus feed -- likely a review
rejection, and not very useful even if accepted. Three ways through,
before opening a merge request:

- **Ship it anyway, clearly labelled.** Description and metadata state
  outright that this is a demo/personal-infrastructure client, not a
  general-purpose bus tracker -- honest, but likely still a rejection on
  review, and even if accepted, of near-zero use to anyone who installs
  it from search.
- **Make the backend URL genuinely runtime-configurable** (a first-run
  settings screen, not a rebuild) so the F-Droid build is a real general
  client anyone can point at their own deployment. The correct fix if
  this milestone is worth doing at all, but it's new app functionality,
  not a submission checklist item -- scope it as its own piece of work
  first.
- **Don't submit to the public catalog; stay on the self-hosted F-Droid
  repo path `android-distribution.md` already chose.** Zero new app
  work, zero review risk, and the app never claims to be something it
  isn't. The default until one of the above is deliberately chosen.

**If runtime-configurable backend is built and the decision is to
proceed, the mechanical submission steps are:**

1. Fork [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) and add
   `metadata/com.thomaspeoples.wraithbus.yml` describing how to build
   from this repo's public Gitea URL: `Categories`, `License: MIT`,
   `SourceCode`, `IssueTracker`, and a `Builds:` entry per `versionCode`
   pinning the exact commit, `gradle: yes`, `output:` the built APK path.
2. Verify locally with `fdroid build --local
   com.thomaspeoples.wraithbus:<versionCode>` (requires `fdroidserver`,
   not installed anywhere in this stack today) -- must succeed from a
   clean clone with no `local.properties`, proving the same "F-Droid's
   build-server state" story `android-distribution.md` already
   documents for the self-hosted repo path.
3. Run `fdroid checkupdates --auto` to confirm the metadata's
   update-detection matches this repo's real tagging scheme
   (`android-X.Y.Z-debug` release tags).
4. Open a merge request against `fdroiddata` with the new metadata file.
   F-Droid's own reviewers take it from there -- expect requests for
   changes; this is a third-party project's process, not one this repo
   controls.
5. Once accepted and the first build lands, add an F-Droid badge/link to
   `README.md` and `docs/android-distribution.md`, and drop this repo's
   Gitea Releases page as the primary distribution channel down to
   secondary (F-Droid's own update mechanism supersedes
   `UpdateChecker.kt`'s courtesy check for anyone who installs it that
   way).

**Not in scope for this milestone regardless of the decision above:**
standing up `fdroidserver` for a *second*, official F-Droid repo (the
self-hosted repo already covers "install via F-Droid app" for this
project's own use); Play Store distribution (never planned, contradicts
this project's whole self-hosted premise).

## 1.7.0 -- home screen widget

Not started. Ordered after 1.6.0 on purpose -- do not build before then.

**Warning, read before picking this up:** a live widget needs either
`ACCESS_COARSE_LOCATION` (idea 1 below) or a background-refresh path
tighter than Android's 15-minute `WorkManager` floor (idea 2) -- both
are real scope, not a quick add-on, and both are currently things this
app deliberately does *not* hold (see 1.5.0: location permissions were
stripped from the manifest specifically because the app carried them
unused). Don't start this without picking one of the three ideas below
first and accepting its specific cost; "just add a widget" is not a
bounded task.

Ideas, for whenever this gets picked up:

1. **Nearest stop, next departure.** Glance widget reading the device's
   last-known location and showing the next 1-2 predicted arrivals at
   the nearest stop. Needs `ACCESS_COARSE_LOCATION` -- a real tradeoff,
   not a free add, for an app whose FAQ currently says outright "no,
   this app does not request or use your location."
2. **Pinned line.** Pick one line/direction once in-app; the widget shows
   its live buses' ETAs to a chosen stop. A live-feeling refresh needs
   `AlarmManager` or a foreground-adjacent update path -- vanilla
   `WorkManager`'s 15-minute floor makes ETAs stale before they're read.
3. **Fleet health strip.** No location or line picker at all -- just
   `/metrics/summary` (feed age, buses tracked) as a small always-current
   strip. Cheapest to build (reuses `DashboardScreen`'s existing endpoint
   verbatim), least useful day to day.

(1) is the one worth having; (2) is what a habitual rider actually wants
but costs the most to keep live; (3) is the fallback if neither the
location permission nor the background-refresh plumbing is worth it.
Pick one before this becomes real work, not just a scope.

## 1.8.0 -- split the Android app into its own repo

Landed 2026-09-17. `android/` had been described as "a real, standalone
Gradle project alongside `backend/`, `dbt/`, `ingestor/`, `archiver/`"
since it landed in 1.5.0 -- this milestone made that literally true:
this repo, not a sibling directory inside `wraith-bus-ingestor`. History
preserved via `git subtree split --prefix=android -b android-split` in
the platform repo, fetched into this repo as `feature/TJP-17090` and
merged into `main` with `--allow-unrelated-histories` (this repo's own
`main` started from an independent initial commit, so the two histories
share no common ancestor -- expected, not an error). `docs/ROADMAP.md`
and `docs/android-distribution.md` moved over on `feature/TJP-17091`,
this commit.

**Done:**

- History-preserving move (`git subtree split`, not a plain `git mv`).
- `docs/android-distribution.md` and this file, moved and re-pathed
  (`android/app/...` -> `app/...` now that the app is the repo root).
- **Releases migrated.** All three existing `android-X.Y.Z-debug`
  releases (`1.0.0`, `1.0.1`, `1.0.2`) recreated here via the Gitea API,
  same tag names, same release bodies (with a migration note added), same
  `wraith-bus-debug.apk` assets re-uploaded from the originals (byte
  sizes verified to match exactly). `target_commitish` on each points at
  the equivalent commit in this repo's own subtree-split history, not
  the old repo's commit hash -- those hashes don't exist here, the split
  rewrote them. `UpdateChecker.kt`'s `RELEASES_URL` now points at this
  repo (`wraith-bus-ingestor-apk`), and `docs/android-distribution.md`'s
  Obtainium instructions do too. The old releases on `wraith-bus-ingestor`
  were left in place, not deleted -- an existing Obtainium install
  pointed at the old repo needs manually repointing at `-apk`; there is
  no way to redirect it from the Gitea side.
- **The `style.json` palette coupling got a repo worse.**
  `TrackingScreen.kt`'s map style and colours are a hand-kept
  translation of `ghost-site`'s own `MAP_PALETTE` (see the route-colour
  fix in 1.5.0) -- that was already a two-repo sync problem; it is now a
  three-repo one (`ghost-site`, this repo, and the platform repo's own
  web map reference). No new mechanism proposed here, just naming the
  cost.
- **Versioning and CI are still borrowed.** This repo needs its own
  release automation (cutting `android-X.Y.Z-debug` tags, building and
  attaching the APK) independent of the platform repo's `.cz.yaml` --
  right now there is no CI here at all; `versionCode`/`versionName` in
  `app/build.gradle.kts` are already this repo's own authority, they
  just aren't wired to anything that acts on them yet.
- **`API_BASE_URL`.** Unaffected by the split either way -- it's a
  build-time constant pointing at the platform's public API regardless
  of which repo the Kotlin lives in. Noted here only so it isn't
  mistaken for something this split was supposed to fix.

## 1.9.0 -- CI, docs publishing and automated releases

Not started. Closes the "versioning and CI are still borrowed" gap 1.8.0
left open: this repo currently has no `.gitea/workflows/` at all, no
tests, no published docs, and every release has been cut by hand. Target
is parity with `wraith-bus-ingestor`'s own pipeline -- push a branch, CI
gates it, auto-merge lands it, main cuts a tag and ships a signed APK
without a human touching anything.

### Ground truth this plan is built on

Measured, not assumed, before any of it was written:

| Thing | Reality |
|---|---|
| `ci-actions/auto-merge` | Pure bash + `curl` + `python3` JSON parse, runs on `ubuntu-latest`. Language-agnostic -- **works here unmodified**. |
| `ci-actions/version-bump` | Runs `cz bump`, reads the *target repo's* `.cz.yaml`. The action itself needs no change; this repo needs its own `.cz.yaml`. |
| `ci-images` | One Dockerfile, `FROM gitea/act_runner:latest` = **Alpine/musl**, `apk add`, Python-centric. Built and pushed by hand. |
| Runner labels | `gitea/docker-compose.yml`: `GITEA_RUNNER_LABELS=ubuntu-latest:docker://node:18-bullseye,ghost-runner:docker://...`. A new label is a one-line edit plus a runner restart. |
| `ubuntu-latest` here | Is **`node:18-bullseye`**, not a real GitHub runner. No JDK, no Android SDK. |
| Host | 6 cores, 15GB RAM, ~3GB available, 63GB disk free, nightly dbt timer at 03:00. |
| This repo | AGP 8.13.2, Kotlin 2.2.0, Gradle 8.13, compileSdk 36, JDK 17, `app/src/main` only -- **zero tests**. |

### Decisions, made up front

**A new runner image, and it has to be Debian, not Alpine.** Not a
preference: Google ships `aapt2` and `d8` as glibc-linked binaries, and
they do not run on musl without `gcompat` shims that are known-flaky.
Extending the existing Alpine `ghost-runner` is a dead end regardless of
how the Gradle-vs-PyTest separation is felt about -- though that
separation is wanted independently, and this delivers it.

- `ci-images/Dockerfile.android`, added **alongside** the existing root
  `Dockerfile` -- not a subdirectory restructure, which would break the
  documented build command and that file's own `COPY pyproject.toml .`
  build context for no gain.
- Tag `git.thomaspeoples.com/thomaspeoples/ci-actions/ghost-runner-android:latest`,
  matching the existing image's registry path. Label
  `ghost-runner-android`.
- `eclipse-temurin:17-jdk-jammy` + cmdline-tools + platform 36 +
  build-tools **35.0.0** + platform-tools + git/curl, plus `python3-pip`
  with `mkdocs` and `mkdocs-material` so the docs job doesn't need a
  second image or cross-job artifact passing (Gitea act_runner's
  artifact support is not worth depending on). Built and measured at
  1.75GB, against a 1.8GB estimate.

  Two corrections this milestone made to its own plan, both found by
  running a real build rather than reading documentation: build-tools is
  **35.0.0, not 36.1.0** (AGP 8.13.2 resolves 35.0.0 even at
  `compileSdk 36`, and pinning the wrong one silently re-downloads the
  SDK on every CI run rather than failing), and `genbadge` is **not**
  usable here -- it only parses Cobertura XML while Kover emits JaCoCo,
  so `bin/coverage_badge.py` renders the badge from stdlib instead.
- `ANDROID_HOME`/`ANDROID_SDK_ROOT` set as ENV in the image. AGP falls
  back to these when `local.properties` is absent -- which is exactly
  the failure hit locally the first time this repo was built standalone
  after the 1.8.0 split, and exactly the state F-Droid's build server
  would see too (see 1.6.0).

**Version authority: `.cz.yaml` with `version_provider: scm`.** No
`pyproject.toml` in a Kotlin repo just to hold a number. `scm` reads the
current version straight from the latest git tag, so there is no file to
keep in sync. `tag_format: android-$version-debug` is **non-negotiable**:
`UpdateChecker.kt` matches `^android-(\d+)\.(\d+)\.(\d+)-debug$` and
Obtainium's asset matcher depends on the same shape (see
[docs/android-distribution.md](android-distribution.md)).

**`versionCode` derived from `versionName`, not stored separately.**
This kills the bug class 1.5.0 already hit, where `versionCode` was
silently never bumped across the `1.0.0` -> `1.0.1` re-ship:

```kotlin
val appVersionName = "1.0.3"   // the only line cz rewrites
versionCode = appVersionName.split(".")
    .let { (maj, min, patch) -> maj.toInt() * 10000 + min.toInt() * 100 + patch.toInt() }
```

A one-way door, stated plainly: `1.0.3` becomes `10003`, and there is no
going back to small numbers afterwards. Safe from here -- the installed
build carries `versionCode 2`, so the jump is still an upgrade -- and
monotonic, which is what F-Droid actually requires.

**Signing, and the trap it defuses.** The debug keystore is generated
**per machine**. A CI-built debug APK would carry a different signature
than the locally-built one already installed on Tegu, and Android would
refuse the upgrade outright with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
So: one real release keystore, base64 in a Gitea secret, a proper
`release` signingConfig. Drops the "debug build, not signed" caveat
1.5.0 recorded, and 1.6.0 needs it anyway. **Cost: one uninstall and
reinstall on Tegu at the cutover** -- unavoidable, and doing it later
only means doing it later.

**Tests: a small real suite with an honest floor.** No emulator in CI, so
only pure logic is reachable. Seed exactly what actually broke in 1.5.0:
version comparison, ISO-to-local-time formatting, and `BusApi`'s JSON
parsing. Needs three `private` -> `internal` changes to make those
functions visible to the test source set, and
`testImplementation("org.json:json:...")`, because `org.json` is stubbed
on JVM unit tests and otherwise silently returns defaults rather than
parsing anything. Kover produces the coverage badge; the floor starts at
**15%**, documented as deliberately low because Compose UI needs
instrumentation to reach -- not dressed up as a number it isn't.

**Badges: only the true ones.** No F-Droid badge (not submitted, see
1.6.0), no Play Store, no App Store. The Obtainium badge gets vendored
into `docs/assets/` rather than hotlinked from GitHub raw -- this is a
self-hosted stack. The release-version badge is generated locally as
`release.svg` alongside `coverage.svg`, not fetched from shields.io's
dynamic-JSON endpoint pointed at the Gitea API.

### Pipeline shape

Branch push and main push, mirroring the platform repo's own split:

- **`test.yml`** -- `on: push: branches-ignore: [main]`, `runs-on:
  ghost-runner-android`: `assembleDebug` + `lint` + `testDebugUnitTest` +
  Kover. Then the `auto-merge` job on `ubuntu-latest`, calling the shared
  action unmodified.
- **`release.yml`** -- `on: push: branches: [main]`, one workflow, two
  jobs:
  - `bump`, `runs-on: ghost-runner` (the existing Alpine image already
    has commitizen baked in; bumping is pure git + cz and needs no
    Android SDK), guarded by
    `if: "!contains(github.event.head_commit.message, '[skip ci]')"`.
  - `release-apk`, `needs: bump`, `runs-on: ghost-runner-android`:
    builds the signed APK, creates the Gitea Release, uploads
    `wraith-bus-debug.apk`.

  **No tag-triggered workflow.** The open question was whether the
  version-bump action's `--tags` push re-triggers Actions on this Gitea
  version; it's moot, because the `[skip ci]` guard is the stack's
  existing answer to exactly this. The bump commit message already ends
  `[skip ci]` (set by `.cz.yaml`'s `bump_message`), so the re-triggered
  run on main hits the guard and stops. Same pattern every other repo in
  the stack uses -- one mechanism, not a second one invented here.

  One real detail: `release-apk` must check out `ref: main` with
  `fetch-depth: 0`, **not** the triggering SHA. `actions/checkout@v4`
  defaults to the commit the run started at, which is the commit
  *before* `bump` pushed -- checking that out would build and ship the
  old `versionName`. Reading the version back with
  `git describe --tags --abbrev=0 --match 'android-*'` keeps the tag as
  the single source of truth.
- **`pages.yml`** -- `runs-on: ghost-runner-android`: Kover ->
  `bin/coverage_badge.py` -> `mkdocs build` -> push to `ghost-site` at
  `www/thomaspeoples/gitea-repos/wraith-bus-ingestor-apk`. Note the
  path: the platform repo's own `pages.yml` records that ghost-site's
  2026-09 docroot split moved this, and that **every other `wraith-*`
  repo still writes to the old location nginx no longer serves**. This
  repo starts on the correct one.

### Execution model -- ultracode, 11 scoped agents

Every prompt carries the exact path and the full file body or an exact
old/new string. No agent reads the repo to work out what to do, chooses
between options, or makes a design decision -- all of those are above.
Each returns `{file, changed, summary}` and nothing else, so no agent's
working context comes back to the orchestrator. Sized for Haiku /
Sonnet-low.

| Phase | # | Sole scope | Task |
|---|---|---|---|
| Scaffold | A1 | `mkdocs.yml` | New, mirroring the platform's, `site_url` at this repo's path |
| Scaffold | A2 | `docs/index.md` | New |
| Scaffold | A3 | `docs/ci.md` | New -- image, labels, secrets, release flow |
| Scaffold | A4 | `.cz.yaml` | `version_provider: scm`, `tag_format: android-$version-debug`, platform's `bump_map`/`commit_parser` |
| Scaffold | A5 | `.gitea/workflows/test.yml` | Branch CI + auto-merge |
| Scaffold | A6 | `.gitea/workflows/release.yml` | The two-job `[skip ci]`-guarded release above |
| Scaffold | A7 | `.gitea/workflows/pages.yml` | Docs + badges to ghost-site |
| Kotlin | B1 | `app/build.gradle.kts` | versionCode derivation, release signingConfig, Kover, `org.json` test dep |
| Kotlin | B2 | 3 exact visibility edits | `private` -> `internal` in `UpdateChecker.kt`, `TrackingScreen.kt` |
| Kotlin | B3 | `app/src/test/**` | Three new test files, bodies supplied verbatim |
| Image | C1 | `ci-images/Dockerfile.android` + README | Separate repo, runs last, no overlap |

A1-A7 parallel, then B1-B3 parallel (disjoint files), then C1.

**Not agent work, and not delegated:**

- Building and pushing the image (~15 min, wants disk and RAM headroom).
- Registering the `ghost-runner-android` label and restarting the
  runner. **act_runner caches its registration in `.runner`, so labels
  may need a re-register rather than picking up on restart** -- confirm
  the label actually appears in Gitea's runner list before trusting any
  workflow that targets it.
- Generating the keystore and adding the Gitea secrets
  (`ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, plus `NTFY_TOPIC` so CI
  builds ship with push working rather than degrading to none).
- The Tegu uninstall/reinstall at the signing cutover.
- Verification: `./gradlew testDebugUnitTest lint assembleDebug` run for
  real before anything is pushed.

### Also decided

- **Gradle memory caps are mandatory here, not tuning.** ~3GB available
  on a box with OOM history (see the platform repo's own 1.1.1):
  `org.gradle.daemon=false`, `-Xmx1536m`, `org.gradle.workers.max=2` in
  CI, and CI must not race the 03:00 nightly dbt build.
- **Persist `~/.gradle`** as a named volume on the runner, or every
  build re-downloads several hundred MB of dependencies.
- **detekt/ktlint deferred.** Real value, but a second gate with its own
  baseline-file ceremony. Add once this pipeline is green, not as part
  of standing it up.
- `app/src/test` is the entire testable surface without an emulator.
  The coverage number must not be allowed to imply otherwise.

## Known issues -- raised, not yet actioned

- **Unexpected version bump on the first real release.** `.cz.yaml`'s
  `version_provider: scm` bumped `1.0.2` -> `1.1.0` (MINOR) instead of
  the expected `1.0.3` (PATCH). Cause: commitizen's bump-type scan walks
  the full commit history since the last tag, and picked up an old
  `feature/`-tagged commit still present in the ancestry from the
  `git subtree split` that created this repo. Not fixed -- next options
  are either re-pointing `.cz.yaml`'s history scan to this repo's own
  first commit, or accepting scm-provider bumps are unreliable across a
  subtree-preserved history and hand-pinning the next tag once.
- **"A few bugs" after the Tegu signing cutover**, not yet detailed or
  triaged. Flagged at cutover time, no repro or writeup exists yet.

## 2026-10-01 -- distribution and alert-topic changes

- **Old releases deleted, "debug" dropped from release naming.** The four
  `android-1.0.0` to `android-1.1.0-debug` releases and tags were deleted from
  Gitea: those APKs had the ntfy topic compiled in (`BuildConfig`), and the repo
  and its Releases are publicly readable. Tags are now `android-X.Y.Z` and the
  asset is `wraith-bus.apk` (`.cz.yaml`, `release.yml`, `UpdateChecker.kt`). The
  history above that says `-debug` is what was true at the time. CI builds the
  releases again, from a clean slate, starting from 1.0.0.
- **The ntfy topic is a setting, not a build input.** About > Alerts stores it
  in the app's private preferences (`ntfy/TopicStore.kt`); saving restarts the
  listener, blank turns alerts off. `NTFY_TOPIC` and `NTFY_WS_URL` are gone
  from `BuildConfig`, `local.properties`, `release.yml` and the Gitea secrets.
  The old topic was rotated on the platform side.
- **Data source note updated.** The platform now reads the published BODS GTFS
  timetable for route shape, stop sequences, next stops and line search
  (platform ADR-0008), so the "no static timetable ingest" paragraph under
  Known issues is out of date: routes are no longer inferred from observed
  positions, except London, where TfL publishes no BODS timetable.
