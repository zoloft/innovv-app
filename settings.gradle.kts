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

rootProject.name = "innovv-open-camera"
include(":composeApp")
include(":androidApp")

// Local development: build the sibling SDK from source.
includeBuild("../sdk") {
    dependencySubstitution {
        substitute(module("com.zoloft.innovv:innovv-sdk"))
            .using(project(":innovv-sdk"))
    }
}
