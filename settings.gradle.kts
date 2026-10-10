pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}
rootProject.name = "tomato-notes"
include(":app")

val simpleCommonsDir = rootDir.resolve("Simple-Commons")
if (simpleCommonsDir.exists()) {
    includeBuild("Simple-Commons") {
        dependencySubstitution {
            substitute(module("com.github.SimpleMobileTools:Simple-Commons")).using(project(":commons"))
        }
    }
}
