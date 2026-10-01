# wraith-bus-ingestor-apk

The Android app for
[wraith-bus-ingestor](https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor):
a live UK bus tracker for LineageOS, full native Jetpack Compose, no WebView
wrapper. Split out of that repo's `android/` directory on roadmap milestone
1.8.0, history preserved, so the app has its own versioning and release
cadence separate from the ingest/API platform it talks to.

## What's here

- **Tracking** -- a MapLibre map of live bus positions, refreshed every 60s
  from the platform's `/buses` endpoint, using the same tile source and
  style as the web map. Tap a bus to draw its route and open its detail
  sheet; search by locality, line number or bus id.
- **Dashboard** -- pipeline health and punctuality, read from the
  platform's `/metrics/summary` and `/metrics/punctuality` endpoints
  rather than querying QuestDB or Grafana from the phone.
- **Push** -- an optional foreground service holding a WebSocket to the
  same self-hosted ntfy topic the platform's Grafana alerts publish to. The
  topic ID is entered in About > Alerts, never built into the APK.

## Scope, stated plainly

This is a single-device app. The backend API base URL is baked in at build
time and is not repointable, so it is a client for one specific self-hosted
deployment rather than a general-purpose bus tracker. See
[the roadmap](ROADMAP.md) milestone 1.6.0 for what that means for public
F-Droid distribution, and [distribution](android-distribution.md) for the
ways to actually install it.

## Where the data comes from

Live vehicle positions are the UK Bus Open Data Service's SIRI-VM feed,
polled every 60 seconds by the platform.

The platform now reads the published BODS GTFS timetable for route shape and
stop sequences, next stops and line search (platform ADR-0008). ETAs are still a
regression fitted to observed positions, with a timetable-pinned rung tried
first where a bus can be matched to a scheduled trip. London is the gap: TfL
does not publish to BODS, so London buses have a position, an ETA and a place
in the live list, but no route overlay, next stop or line-search entry.
