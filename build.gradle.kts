plugins {
    alias(libs.plugins.kotlin.mpp) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dokka) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

subprojects {
    group = "io.github.tree-sitter"

    if (System.getenv("CI") != null) {
        tasks.withType(AbstractTestTask::class) {
            testLogging.events("passed", "skipped", "failed")
        }
    }
}

tasks.wrapper {
    gradleVersion = "8.7"
    distributionType = Wrapper.DistributionType.BIN
}
