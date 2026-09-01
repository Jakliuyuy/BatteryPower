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
        // Xposed / LSPosed API 82
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "BatteryPower"
include(":app")
