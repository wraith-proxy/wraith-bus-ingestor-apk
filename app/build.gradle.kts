import java.util.Properties

// The one place this app's version is written down. commitizen rewrites the
// appVersionName line on bump (.cz.yaml version_files points at it), and the
// android-X.Y.Z tag is cut FROM this value, never invented alongside
// it.
//
// versionCode is DERIVED, never stored: when it was maintained by hand it
// was silently never bumped across the 1.0.0 -> 1.0.1 re-ship and both
// builds shipped as code 1. Deriving it makes that failure impossible.
//
//     1.0.3  ->  10003
//
// One-way door: the old hand-maintained codes were small integers (the
// installed build carries 2), so this jump is still an upgrade, but there
// is no going back to small numbers afterwards. Monotonic, which is what
// F-Droid requires.
// 0.0.0 is a placeholder: there are no tags yet, so commitizen reads the current
// version as 0.0.0 and rewrites this line to the first real one on bump.
val appVersionName = "0.0.0"
val appVersionCode = appVersionName.split(".").let { (major, minor, patch) ->
    (major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt())
        .coerceAtLeast(1) // the 0.0.0 placeholder must still be a legal versionCode
}

// local.properties is gitignored (see local.properties.example) -- reading
// it here, not hardcoding a URL into a committed .kt file, is the point.
// The ntfy topic is deliberately NOT here: it is a credential, so it is
// typed into the app on the device (ntfy/TopicStore.kt), never baked into
// an APK.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Order matters: local.properties first (a developer's own machine), then a
// gradle property (how CI injects a secret, via ORG_GRADLE_PROJECT_*), then
// the environment, then the default. CI has no local.properties at all, by
// design -- same state F-Droid's build server sees.
fun localProp(key: String, default: String): String =
    (localProps.getProperty(key)
        ?: (if (project.hasProperty(key)) project.property(key) as String else null)
        ?: System.getenv(key)
        ?: default)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlinx.kover")
}

android {
    namespace = "com.thomaspeoples.wraithbus"
    // Android 16 (API 36) end to end -- see docs/ROADMAP.md 1.5.0. This app
    // targets exactly one device (Tegu, a Pixel 9a phone running
    // LineageOS), so there is no reason to widen minSdk for compatibility
    // it will never need to exercise.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thomaspeoples.wraithbus"
        minSdk = 36
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        // Single-device scope (Tegu, a Pixel 9a phone) -- arm64-v8a is the
        // only architecture that will ever run this APK. Shipping the other
        // three MapLibre native slices quadruples the native-lib payload
        // for zero real devices.
        ndk {
            abiFilters += "arm64-v8a"
        }

        buildConfigField(
            "String", "API_BASE_URL",
            "\"${localProp("apiBaseUrl", "https://www.thomaspeoples.com/api/")}\""
        )
    }

    // The debug keystore Android generates is per-machine, so an APK signed
    // with CI's would not install over one signed on a developer's laptop.
    // A stable key is what makes an automated release upgradeable at all.
    // Absent the env vars (any local build) this config is simply unused.
    signingConfigs {
        create("release") {
            val storePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!System.getenv("ANDROID_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // core-ktx 1.19.0 / compose 1.12.x / lifecycle 2.11.x all bumped their
    // minimum compileSdk to 37 in the same release wave -- 37 is still an
    // active preview cycle on this SDK (android-37.0/.1/.2, no plain stable
    // "37" yet), not the stable release docs/ROADMAP.md 1.5.0 actually
    // targets. One step back on each keeps compileSdk 36 (real, stable,
    // matches "Android 16+ minSdk") rather than chasing a preview platform
    // for a single-device app that ships once.
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.4")

    // Compose libraries pinned directly, not via a BOM -- one fewer moving
    // part, and the versions below are already a matched, verified set.
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.ui:ui-graphics:1.11.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.runtime:runtime:1.11.4")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // 2.10.1 transitively pulls lifecycle-*-compose 2.11.0, same
    // compileSdk-37 requirement as above -- one more step back.
    implementation("androidx.navigation:navigation-compose:2.9.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")

    // Networking + push, per docs/ROADMAP.md 1.5.0's explicit stack choice:
    // full native, Jetpack Compose + OkHttp, no Retrofit/Moshi -- org.json
    // (bundled in the Android platform) is enough for a handful of
    // hand-shaped endpoints, and OkHttp's own WebSocket client covers ntfy.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Same vector tile source and style the web map (ghost-site's
    // www/slaycoach/bus-map/) already renders -- see assets/style.json,
    // translated from that page's own MapLibre style object, not a second
    // cartography invented for this app.
    implementation("org.maplibre.gl:android-sdk:11.11.0")

    // Unit tests only: no emulator in CI, so the testable surface is the
    // pure logic behind the sheet and the update check.
    testImplementation("junit:junit:4.13.2")
}
