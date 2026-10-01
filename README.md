[![Documentation](https://img.shields.io/badge/docs-live-brightgreen)](https://www.thomaspeoples.com/gitea-repos/wraith-bus-ingestor-apk/)
![Coverage](https://www.thomaspeoples.com/gitea-repos/wraith-bus-ingestor-apk/coverage.svg)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Get it on Obtainium](https://img.shields.io/badge/get%20it%20on-Obtainium-1f2429?logo=android&logoColor=3ddc84)](https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor-apk/releases)
![Platform](https://img.shields.io/badge/platform-Android%2016%2B-3ddc84)

# 👻 wraith-bus-ingestor-apk
### *Live UK Bus Tracking, on the phone*

The Android app for
[wraith-bus-ingestor](https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor).
Full native Jetpack Compose, not a WebView wrapper: a MapLibre map of live
buses, a dashboard of pipeline health, and push notifications from the same
self-hosted ntfy topic the platform's Grafana alerts publish to.

Split out of that repo's `android/` directory on roadmap 1.8.0 with its
history preserved, so the app has its own versioning and release cadence
separate from the ingest and API platform it talks to.

---

## 📲 Installing

There is no Play Store build, deliberately. CI builds and signs every release
on merge to `main`; the APK is `wraith-bus.apk` on the
[Releases](https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor-apk/releases)
page, tagged `android-X.Y.Z`.

- **Obtainium:** point it at this repo's URL and tell it the host is Gitea. It
  tracks releases automatically.
- **Direct sideload:** download the APK from Releases and open it. The app also
  checks for a newer release itself on launch.
- **F-Droid:** self-hosted repo only, not the public catalog. See
  [docs/ROADMAP.md](docs/ROADMAP.md) 1.6.0.

Then open **About > Alerts** and enter the ntfy topic ID to turn on push
alerts, or leave it blank for none. The topic is a credential, so it is typed in
on the device and never built into the APK. A public APK that carried it would
give it to everyone who downloaded the file.

## 🗺️ What it does

```
platform API  (/buses, /bus/<id>, /route, /search, /metrics/*)
  |
  +-- Tracking ----------------------------------------------+
  |   MapLibre map, same tiles and palette as the web map     |
  |   live positions every 60s -- tap a bus, its route draws  |
  |   and the camera fits to it before the sheet opens        |
  |   search by locality, line number ("X93") or bus id       |
  +-----------------------------------------------------------+
  |
  +-- Dashboard ---------------------------------------------+
  |   feed age, buses tracked, punctuality, ETA model fit     |
  |   read from the API, never from QuestDB or Grafana direct |
  +-----------------------------------------------------------+
  |
  +-- Push --------------------------------------------------+
  |   foreground service, WebSocket to self-hosted ntfy       |
  |   pipeline alerts only -- not bus arrival alerts          |
  +-----------------------------------------------------------+
```

## 🧩 The Hard Decisions Baked In

**`versionCode` is derived, not stored.** `major * 10000 + minor * 100 +
patch`, computed in `app/build.gradle.kts` from the one `versionName`
string commitizen rewrites. It is derived because when it was maintained by
hand it was silently never bumped across the 1.0.0 to 1.0.1 re-ship, and
both builds shipped as code 1.

**Location permissions are actively stripped.** MapLibre's own manifest
merges in `ACCESS_COARSE_LOCATION`/`ACCESS_FINE_LOCATION` for a "show my
position" feature this app never enables. They are removed with
`tools:node="remove"` rather than carried unused -- an app asking for
location it does not use is exactly what a reviewer should flag.

**It cannot be repointed at another server.** The API base URL is baked in
at build time. That is honest about what this is: a client for one
self-hosted deployment, not a general-purpose bus tracker.

**Routes come from the published timetable.** The platform now reads the
published BODS GTFS timetable for route shape and stop sequences, next stops and line search (platform ADR-0008). ETAs are still a
regression fitted to observed positions, with a timetable-pinned rung tried
first where a bus can be matched to a scheduled trip. London is the gap: TfL
does not publish to BODS, so London buses have a position, an ETA and a place
in the live list, but no route overlay, next stop or line-search entry.

## 🛠️ Building

```bash
git clone https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor-apk.git
cd wraith-bus-ingestor-apk

cp local.properties.example local.properties   # set sdk.dir, apiBaseUrl
./gradlew assembleDebug
```

`local.properties` is gitignored. The ntfy topic is a credential (anyone holding
it can read every alert), so it is never built into the APK: type it into
About > Alerts on the device. Blank means no push notifications.

CI builds the releases on a purpose-built image; see [docs/ci.md](docs/ci.md).

## License

MIT. See LICENSE.

---

<div align="center">
<sub><i>"Sovereignty is found in the Source."</i></sub>
</div>
