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

include(":ktreesitter-dsl")

file("languages").listFiles { file -> file.isDirectory }?.forEach {
    include(":languages:${it.name}")
}
