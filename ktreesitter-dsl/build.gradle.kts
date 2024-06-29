import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

version = property("project.version") as String

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.kotlin.mpp)
}

buildscript {
    dependencies {
        classpath(libs.dokka.base)
    }
}

kotlin {
    js(IR) {
        moduleName = project.name

        binaries.library()

        nodejs {
            testTask {
                useMocha()
            }
        }

        useEsModules()
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
            }
        }

        jsTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

tasks.create<Jar>("javadocJar") {
    group = "documentation"
    archiveClassifier.set("javadoc")
    from(files(rootDir.resolve("README.md")))
}

publishing {
    publications.withType(MavenPublication::class) {
        val ref = System.getenv("GITHUB_REF_NAME") ?: "master"
        artifact(tasks["javadocJar"])
        pom {
            name = "KTreeSitter DSL"
            description = "Tree-sitter grammar DSL in Kotlin"
            url = "https://github.com/tree-sitter/kotlin-tree-sitter/blob/$ref/ktreesitter-dsl"
            inceptionYear = "2024"
            organization {
                name = "tree-sitter"
                url = "https://github.com/tree-sitter"
            }
            licenses {
                license {
                    name = "MIT License"
                    url = "https://spdx.org/licenses/MIT.html"
                }
            }
            developers {
                developer {
                    id = "ObserverOfTime"
                    name = "ObserverOfTime"
                    email = "chronobserver@disroot.org"
                    url = "https://github.com/ObserverOfTime"
                }
            }
            scm {
                url = "https://github.com/tree-sitter/kotlin-tree-sitter"
                connection = "scm:git:git://github.com/tree-sitter/kotlin-tree-sitter.git"
                developerConnection = "scm:git:ssh://github.com/tree-sitter/kotlin-tree-sitter.git"
            }
            issueManagement {
                system = "GitHub Issues"
                url = "https://github.com/tree-sitter/kotlin-tree-sitter/issues"
            }
            ciManagement {
                system = "GitHub Actions"
                url = "https://github.com/tree-sitter/kotlin-tree-sitter/actions"
            }
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

signing {
    isRequired = System.getenv("CI") != null
    if (isRequired) {
        val key = System.getenv("SIGNING_KEY")
        val password = System.getenv("SIGNING_PASSWORD")
        useInMemoryPgpKeys(key, password)
    }
    sign(publishing.publications)
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}

plugins.withType<NodeJsRootPlugin> {
    the<NodeJsRootExtension>().download = false
}

plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().apply {
        download = false
        yarnLockAutoReplace = true
        yarnLockMismatchReport = YarnLockMismatchReport.WARNING
    }
}
