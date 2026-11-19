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

file("languages").listFiles {
    it.isDirectory && it.name[0] != '.'
}?.forEach {
    include(":languages:${it.name}")
}
