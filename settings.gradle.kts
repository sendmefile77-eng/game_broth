pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GameBroth"

include(
    ":app",
    ":core:model",
    ":core:storage",
    ":core:simulation",
    ":core:ai-text",
    ":core:ai-image",
    ":core:adult-contracts",
)

// Grok's optional mature-content implementation can live here later without becoming a hard
// dependency of the base app. The core project must always build without it.
if (file("feature/adult").exists()) {
    include(":feature:adult")
}
