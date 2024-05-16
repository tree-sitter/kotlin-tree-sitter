import java.io.ByteArrayOutputStream
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

val os: OperatingSystem = OperatingSystem.current()
val libsDir = layout.buildDirectory.get().dir("tmp").dir("libs")
val treesitterDir = rootDir.resolve("tree-sitter")

version = property("project.version") as String

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.kotlin.mpp)
    // alias(libs.plugins.android.library)
    alias(libs.plugins.kotest)
    alias(libs.plugins.dokka)
}

buildscript {
    dependencies {
        classpath(libs.dokka.base)
    }
}

kotlin {
    // jvm {}

    /* androidTarget {
        withSourcesJar(true)
        publishLibraryVariants("release")
    } */

    when {
        os.isLinux -> listOf(linuxX64(), linuxArm64())
        os.isWindows -> listOf(mingwX64())
        os.isMacOsX -> listOf(
            macosArm64(),
            macosX64(),
            iosArm64(),
            iosSimulatorArm64()
        )
        else -> {
            val arch = System.getProperty("os.arch")
            throw GradleException("Unsupported platform: $os ($arch)")
        }
    }.forEach { target ->
        target.compilations.configureEach {
            cinterops.create("treesitter") {
                val srcDir = treesitterDir.resolve("lib/src")
                val includeDir = treesitterDir.resolve("lib/include")
                includeDirs.allHeaders(srcDir, includeDir)
                includeDirs.headerFilterOnly(includeDir)
                extraOpts("-libraryPath", libsDir.dir(konanTarget.name))
            }
        }
    }

    jvmToolchain(17)

    sourceSets {
        commonMain {
            languageSettings {
                @OptIn(ExperimentalKotlinGradlePluginApi::class)
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }

            dependencies {
                implementation(libs.kotlin.stdlib)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.bundles.kotest.core)
                rootProject.project("languages").subprojects.forEach {
                    implementation(project(":languages:${it.name}"))
                }
            }
        }
    }
}

/*
android {
    namespace = "$group.$name"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        ndk {
            moduleName = "tree-sitter"
            //noinspection ChromeOsAbiSupport
            abiFilters += setOf("x86_64", "arm64-v8a", "armeabi-v7a")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        resValues = false
    }
}
*/

tasks.create<Jar>("javadocJar") {
    group = "documentation"
    archiveClassifier.set("javadoc")
    from(files(rootDir.resolve("README.md")))
}

publishing {
    publications.withType(MavenPublication::class) {
        artifact(tasks["javadocJar"])
        pom {
            name.set("KTreeSitter")
            description.set("Kotlin bindings to the Tree-sitter parsing library")
            url.set("https://tree-sitter.github.io/kotlin-tree-sitter/")
            inceptionYear.set("2024")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://spdx.org/licenses/MIT.html")
                }
            }
            developers {
                developer {
                    id.set("ObserverOfTime")
                    email.set("chronobserver@disroot.org")
                    url.set("https://github.com/ObserverOfTime")
                }
            }
            scm {
                url.set("https://github.com/tree-sitter/kotlin-tree-sitter")
                connection.set("scm:git:git://github.com/tree-sitter/kotlin-tree-sitter.git")
                developerConnection.set(
                    "scm:git:ssh://github.com/tree-sitter/kotlin-tree-sitter.git"
                )
            }
            ciManagement {
                system.set("GitHub Actions")
                url.set("https://github.com/tree-sitter/kotlin-tree-sitter/actions")
            }
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri(rootProject.layout.buildDirectory.dir("repo"))
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

tasks.dokkaHtml {
    moduleName.set("KTreeSitter")
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        footerMessage = "© 2024 tree-sitter"
        customAssets = listOf(rootDir.resolve("gradle/logo-icon.svg"))
    }
    dokkaSourceSets.configureEach {
        jdkVersion.set(17)
    }
}

fun CInteropProcess.zigBuild(target: String, lib: String, vararg args: String) {
    exec {
        executable = "zig"
        workingDir = treesitterDir
        args("build", "--summary", "all", "-Dtarget=$target", *args)
    }

    copy {
        from(treesitterDir.resolve("zig-out/lib/$lib"))
        into(libsDir.dir(konanTarget.name))
    }

    delete(treesitterDir.resolve("zig-out/lib/$lib"))
}

if (os.isLinux) {
    tasks.getByName<CInteropProcess>("cinteropTreesitterLinuxX64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter.a"))

        doFirst {
            zigBuild("x86_64-linux", "libtree-sitter.a")
        }
    }

    tasks.getByName<CInteropProcess>("cinteropTreesitterLinuxArm64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter.a"))

        doFirst {
            zigBuild("aarch64-linux", "libtree-sitter.a")
        }
    }
} else if (os.isWindows) {
    tasks.getByName<CInteropProcess>("cinteropTreesitterMingwX64") {
        outputs.file(libsDir.dir(konanTarget.name).file("tree-sitter.lib"))

        doFirst {
            zigBuild("x86_64-windows-gnu", "tree-sitter.lib")
        }
    }
} else if (os.isMacOsX) {
    fun findSysroot(sdk: String): String {
        val output = ByteArrayOutputStream()
        exec {
            executable = "xcrun"
            standardOutput = output
            args("--sdk", sdk, "--show-sdk-path")
        }
        return output.use { it.toString().trimEnd() }
    }

    tasks.getByName<CInteropProcess>("cinteropTreesitterMacosX64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter.a"))

        doFirst {
            val sysroot = findSysroot("macosx")
            zigBuild("x86_64-macos", "libtree-sitter.a", "--sysroot", sysroot)
        }
    }

    tasks.getByName<CInteropProcess>("cinteropTreesitterMacosArm64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter.a"))

        doFirst {
            val sysroot = findSysroot("macosx")
            zigBuild("aarch64-macos", "libtree-sitter.a", "--sysroot", sysroot)
        }
    }

    tasks.getByName<CInteropProcess>("cinteropTreesitterIosArm64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter.a"))

        doFirst {
            val sysroot = findSysroot("iphoneos")
            zigBuild("aarch64-ios", "libtree-sitter.a", "--sysroot", sysroot)
        }
    }

    tasks.getByName<CInteropProcess>("cinteropTreesitterIosSimulatorArm64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter.a"))

        doFirst {
            val sysroot = findSysroot("iphoneos")
            zigBuild("aarch64-ios-simulator", "libtree-sitter.a", "--sysroot", sysroot)
        }
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}
