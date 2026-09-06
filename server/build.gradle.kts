// Local-only credential-injecting relay for webapp/ -- see CLAUDE.md's
// webapp/server decision. A generic proxy, not per-feature business logic:
// it forwards requests to the real hosts and attaches the right API
// key/header, so those keys never sit in the browser. Binds to 127.0.0.1
// only -- never reachable from the network.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("com.marketnewsmonitor.server.ProxyServerKt")
}

dependencies {
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.kotlinx.json)
}

kotlin {
    jvmToolchain(17)
}
