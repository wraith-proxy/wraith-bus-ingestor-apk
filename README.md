# wraith-bus-ingestor-apk

The Android app for [wraith-bus-ingestor](https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor):
a live UK bus tracker, split out into its own repo so the app has its own
history, versioning and release cadence separate from the backend/ingest
platform it talks to.

This repo starts empty on `main`. The actual `android/` Gradle project is
moving over from `wraith-bus-ingestor` on a feature branch -- see that
repo's `docs/ROADMAP.md`, milestone 1.8.0, for the full plan and the
open decisions (history preservation, release-tag/Obtainium migration,
docs placement) that need settling before that lands.
