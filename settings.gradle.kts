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

// Single project, two personas (product flavors "asmaa" / "maya" — see app/build.gradle.kts)
rootProject.name = "EkatraAI"
include(":app")
