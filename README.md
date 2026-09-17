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

There is no Play Store build, deliberately. Three ways in:

| Route | Works today | Notes |
|---|---|---|
| **Obtainium** | yes | Point it at this repo's URL, tell it the host is Gitea. Tracks releases automatically. |
| **Direct sideload** | yes | Grab `wraith-bus-debug.apk` from [Releases](https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor-apk/releases). The app checks for newer releases itself on launch. |
| **F-Droid** | self-hosted repo only | Not in the public catalog. [docs/ROADMAP.md](docs/ROADMAP.md) 1.6.0 explains honestly why that is a decision rather than an oversight. |

Release tags are `android-X.Y.Z-debug`, one asset each named
`wraith-bus-debug.apk` -- a predictable shape by design, so Obtainium's
matcher only ever needs to match one filename.

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

**Routes are what buses did, not what the timetable said.** There is no
static timetable ingest anywhere in the stack. Stop sequences come from
observed positions matched to the NaPTAN register, which is also why a
thin-data line can come back truncated.

## 🛠️ Building

```bash
git clone https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor-apk.git
cd wraith-bus-ingestor-apk

cp local.properties.example local.properties   # set sdk.dir, apiBaseUrl, ntfyTopic
./gradlew assembleDebug
```

`local.properties` is gitignored: it carries `ntfyTopic`, which is a
credential -- anyone holding it can read every alert this app raises. With
the file absent the build still succeeds and degrades to "no push
notifications", which is exactly the state CI and an F-Droid build server
see.

CI runs on a purpose-built image; see [docs/ci.md](docs/ci.md).

## License

MIT. See LICENSE.

---

<div align="center">
<sub><i>"Sovereignty is found in the Source."</i></sub>
</div>
