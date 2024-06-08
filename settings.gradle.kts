rootProject.name = "ktreesitter"

pluginManagement {
    includeBuild("ktreesitter-plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":ktreesitter")

file("languages").listFiles { file -> file.isDirectory }?.forEach {
    include(":languages:${it.name}")
}
