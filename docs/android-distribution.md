# Android app: distribution

This app is not on the Google Play Store, and this is deliberate, not a
gap to close later — see [docs/ROADMAP.md § 1.5.0](ROADMAP.md). This page
covers the three real ways to get it onto a device instead.

## Obtainium and direct download

Releases are cut by CI, not by hand. A merge to `main` runs `release.yml`,
which `cz bump`s the version, tags `android-X.Y.Z`, builds a signed
`wraith-bus.apk` and attaches it to a Gitea release. Obtainium tracks that
Releases page.

- Add the app in Obtainium with the repo URL:
  `https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor-apk`
- Tell it the host is Gitea in its source picker.
- Anyone can also download `wraith-bus.apk` from the Releases page directly.

The ntfy topic is a credential (whoever holds it can read the alerts), so it is
entered in the app under **About > Alerts** and stored on the device. It is not
a build input and is in no CI secret, which is what makes a public APK safe. The
APK does point at the production API by default, so anyone who installs it uses
that server.

The earlier `android-X.Y.Z-debug` releases were deleted on 2026-10-01 because
those APKs had the topic compiled in. Anyone whose Obtainium config tracked
them has nothing to follow until the first new release.

## F-Droid

Two different things get called "F-Droid," and this app is set up for
the self-hosted one, not the public catalog:

- **The public F-Droid catalog** (`f-droid.org`) requires a merge request
  against [fdroiddata](https://gitlab.com/fdroid/fdroiddata), reviewed by
  F-Droid's own maintainers, who then own the build recipe going forward.
  That is a real submission to a third-party project on the repo owner's
  behalf and is not something to do without the owner explicitly
  deciding to. **Not done as part of this work** — see
  [docs/ROADMAP.md § 1.6.0](ROADMAP.md) for the fuller decision writeup.
- **A self-hosted F-Droid-compatible repository** (via
  [`fdroidserver`](https://f-droid.org/docs/Setup_an_F-Droid_App_Repo/))
  is a static `index.xml` + APK directory that any F-Droid client, or
  Obtainium, can add as a custom repo. This fits the project's whole
  house style -- self-hosted git, self-hosted CI, self-hosted everything
  -- far better than the public catalog does for a single-deployment app
  tied to one backend. **Not stood up as part of this work either** (it
  is new infrastructure -- a service to run and keep patched -- not a
  metadata change), but everything F-Droid's build process itself needs
  is in place:

### What's actually ready

- **Reproducible build.** `app/build.gradle.kts` reads `local.properties` for
  `apiBaseUrl` and falls back to the real production API when it is absent,
  which is the state F-Droid's build server sees on a fresh checkout. The ntfy
  topic is not a build input at all: `NtfyForegroundService` never starts until
  a topic is entered in the app, so a fresh install degrades to "no push
  notifications", not a crash.
- **No proprietary dependencies.** MapLibre (BSD), OkHttp and every
  AndroidX library (Apache 2.0), Kotlin coroutines (Apache 2.0) -- fully
  FOSS build, no Google Play Services, no Firebase/FCM. Push
  notifications go over a plain WebSocket to a self-hosted `ntfy`
  instance, not a Google push channel.
- **MIT-licensed**, `LICENSE` at the repo root.
- **`versionCode`/`versionName` are the real source of truth** for the
  app's version (`app/build.gradle.kts`), not the Gitea release
  tag names -- those get cut *from* this value at ship time. F-Droid
  needs a real, monotonically increasing `versionCode` per build; this
  repo now has one (see the changelog note below for why `versionCode 2`
  follows `1` with no `3` in between).
- **`fastlane/metadata/android/en-US/`** -- the standard
  fastlane/triple-t layout F-Droid (and other store-listing tooling)
  reads directly: `title.txt`, `short_description.txt`,
  `full_description.txt`, `changelogs/<versionCode>.txt` per release, and
  a 512×512 `images/icon.png` rendered from the same vector artwork the
  in-app launcher icon uses (`app/src/main/res/drawable/ic_launcher_foreground.xml`).
- **Only permissions the app actually uses.**
  `ACCESS_COARSE_LOCATION`/`ACCESS_FINE_LOCATION` were merged in
  transitively by MapLibre's own manifest (for a "show my position"
  feature this app never enables) and are explicitly stripped via
  `tools:node="remove"` in `AndroidManifest.xml` -- an app requesting
  location it doesn't use is exactly what an F-Droid reviewer would
  flag, so it is fixed rather than left for a reviewer to catch.

### What a self-hosted F-Droid repo would still need, if stood up later

A real `fdroidserver` instance (or a hand-rolled `index.xml`), a place to
host it, and a build recipe (`metadata/com.thomaspeoples.wraithbus.yml`)
telling `fdroidserver` how to check this repo out and run
`./gradlew assembleDebug` (or a proper signed `assembleRelease`, which
does not exist yet either -- see `docs/ROADMAP.md` 1.5.0's own note on
why a signed release variant was skipped). None of that is built here;
it is new infrastructure, not a metadata change, and is worth doing the
day it is actually wanted, not preemptively.

## In-app update notice

`update/UpdateChecker.kt` checks this repo's Releases page for a newer
`android-X.Y.Z` tag once per cold start and shows a dialog if one exists. It is
a courtesy for direct sideloaders who have neither F-Droid nor Obtainium. It
fails silently on any network error and never blocks the UI.
