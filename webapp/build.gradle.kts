import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// A Chrome desktop client backed by a local proxy (server/) -- NOT a
// second production target, not a refactor of :app, and shares no code
// with it. See CLAUDE.md's webapp/server decision for why data goes
// through the proxy instead of straight to Finnhub/Alpha
// Vantage/EDGAR/Claude (CORS + API-key exposure), and why background
// polling/notifications/the widget are dropped entirely (no browser
// equivalent).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.browser)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
    }
}
