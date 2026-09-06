pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_PROJECT (Gradle's own default), not FAIL_ON_PROJECT_REPOS or
    // PREFER_SETTINGS: the Kotlin Gradle plugin's Node.js/Yarn setup
    // (needed for webapp/'s wasmJs webpack toolchain) registers its own
    // nodejs.org/yarnpkg.com download repository directly on the project,
    // outside this file, to resolve the synthetic "org.nodejs:node:<version>"
    // coordinate it uses to fetch the Node.js binary. FAIL_ON_PROJECT_REPOS
    // rejects that repo outright; PREFER_SETTINGS silently *ignores* it in
    // favor of the repositories below, which don't have Node.js binaries
    // either -- both break :kotlinNodeJsSetup. PREFER_PROJECT actually lets
    // that project-declared repo be consulted for its own resolution, while
    // everything else still falls back to google()/mavenCentral() below.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "market-news-monitor"
include(":app")
include(":webapp")
include(":server")
