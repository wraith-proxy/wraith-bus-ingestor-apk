# CI

Mirrors `wraith-bus-ingestor`'s pipeline: push a branch and it is gated,
merged and released without a human in the loop. Three workflows, two
runner labels, one image.

## The runner image

Android CI runs on **`ghost-runner-android`**, not the `ghost-runner`
label the Python repos use. It has to be a separate image: Google ships
`aapt2` and `d8` as glibc-linked binaries, and `ghost-runner` is
Alpine/musl, so the Android SDK genuinely cannot run there. The image is
built from `ci-images/Dockerfile.android` and published to the Gitea
package registry.

It carries JDK 17, Android platform 36, build-tools 35.0.0 (what AGP 8.13.2
actually resolves, regardless of `compileSdk 36`), plus `mkdocs` so the docs
job needs neither a second image nor cross-job artifact passing.

The coverage badge is rendered by `bin/coverage_badge.py`, not by `genbadge`
the way the platform repo does it: `genbadge` only parses Cobertura XML
(root tag `<coverage>`) and Kover emits JaCoCo (root tag `<report>`), which
it dies on with a bare `AssertionError`. The script is stdlib-only, so it
needs nothing the JDK base image does not already have.

Coverage sits around 4.5% and there is deliberately **no failing gate** on
it. Without an emulator the testable surface is the pure logic behind the
detail sheet and the update check; a threshold here would either be a
number low enough to be meaningless or one that fails every build. The
badge reports the truth instead of dressing it up.

`ANDROID_HOME` is set in the image. `local.properties` is gitignored and
absent in CI, which is deliberate -- it is the same state F-Droid's build
server sees, and AGP falls back to `ANDROID_HOME` rather than failing.

## Workflows

| File | Trigger | Runner | Does |
|---|---|---|---|
| `test.yml` | push to any branch but `main` | `ghost-runner-android` | unit tests + coverage, Android Lint, `assembleDebug`, then auto-merge |
| `release.yml` | push to `main` | `ghost-runner` then `ghost-runner-android` | version bump, tag, build signed APK, publish release. |
| `pages.yml` | push to `main` | `ghost-runner-android` | coverage badge, mkdocs build, publish to ghost-site |

### Why there is no tag-triggered workflow

`release.yml` bumps the version and builds the APK in the same run, as two
jobs joined by `needs`. The bump pushes a commit back to `main`, which
re-triggers the workflow -- and that re-run stops at the
`[skip ci]` guard on the bump job, because `.cz.yaml`'s `bump_message`
ends with `[skip ci]`. Same mechanism every other repo in the stack uses,
rather than a second one invented here.

One detail that matters: the `release-apk` job checks out `ref: main`,
**not** the triggering SHA. `actions/checkout@v4` defaults to the commit
the run started at, which is the commit *before* the bump -- building that
would ship the previous version number.

## Versioning

`.cz.yaml` uses `version_provider: scm`, so the current version is read
from the latest git tag and there is no version file to keep in sync. Tag
format is `android-X.Y.Z`, which is not cosmetic: `UpdateChecker.kt`
matches exactly that shape, and so would Obtainium's asset matcher if releases
were published again.

`versionCode` is **derived** from `versionName` in
`app/build.gradle.kts`, never stored separately:

    1.0.3  ->  10003        (major * 10000 + minor * 100 + patch)

This exists because `versionCode` was silently never bumped across the
1.0.0 to 1.0.1 re-ship, when it was maintained by hand. It is a one-way
door -- there is no going back to small numbers afterwards -- and it is
monotonic, which is what F-Droid requires.

## Secrets

| Secret | Used by | For |
|---|---|---|
| `giteatoken` | all | API auth, pushes, releases |
| `giteaurl` | `test.yml`, `pages.yml` | Gitea base URL |
| `ANDROID_KEYSTORE_B64` | `release.yml` | base64 of the release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | `release.yml` | keystore password |
| `ANDROID_KEY_ALIAS` | `release.yml` | signing key alias |
| `ANDROID_KEY_PASSWORD` | `release.yml` | signing key password |

The ntfy topic is not a CI secret: it is entered in the app on the device, and
the `NTFY_TOPIC` secret was deleted when it stopped being baked into builds.

The keystore is a real one, not the auto-generated debug key. The debug key
is generated **per machine**, so a CI-built APK signed with it would carry a
different signature than a locally built one and Android would refuse the
upgrade outright. Signing with a stable key in CI is what makes automated
releases installable at all.

## Running on this host

The build host is a 6-core, 15GB NAS also running QuestDB and OSRM. The
image sets `org.gradle.daemon=false`, a 1536m heap cap, two workers, and
`kotlin.compiler.execution.strategy=in-process` -- the last one matters
because Kotlin otherwise forks a second compile daemon, which is enough to
push a memory-capped container over. CI should also not race the platform's
nightly dbt build at 03:00.

A local build leaves a Gradle daemon and a Kotlin daemon resident for two
hours by default, costing roughly 2.3GB between them. `./gradlew --stop`
clears them, and is worth doing before anything else heavy runs here.
