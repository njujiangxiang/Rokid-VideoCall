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
        // Rokid SDK 仓库（如果需要）
        // maven { url = uri("https://repo.rokid.com/maven") }
    }
}

rootProject.name = "RokidVideoCall"
include(":app")
