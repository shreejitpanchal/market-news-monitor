import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// A sample-data Chrome UI preview of the app's screens — NOT a second
// production target, not a refactor of :app, and shares no code with it.
// See CLAUDE.md for why (Room/Retrofit/WorkManager/notifications/widget are
// all Android-only; live API calls from a browser would either hit CORS or
// require exposing API keys client-side).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "webapp"
        browser {
            commonWebpackConfig {
                outputFileName = "webapp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
    }
}
