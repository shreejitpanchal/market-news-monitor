pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS, not FAIL_ON_PROJECT_REPOS: the Kotlin Gradle
    // plugin's Node.js/Yarn setup (needed for webapp/'s wasmJs webpack
    // toolchain) registers its own nodejs.org/yarnpkg.com download
    // repositories directly on the project, outside this file -- a hard
    // fail here would reject that repo instead of just deprioritizing it
    // behind the ones declared below.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "market-news-monitor"
include(":app")
include(":webapp")
include(":server")
