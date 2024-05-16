import java.io.ByteArrayOutputStream
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

val os: OperatingSystem = OperatingSystem.current()
val libsDir = layout.buildDirectory.get().dir("tmp").dir("libs")
val grammarDir = projectDir.resolve("tree-sitter-java")
val grammarName = project.name

version = grammarDir.resolve("Makefile").readLines()
    .first { it.startsWith("VERSION := ") }.removePrefix("VERSION := ")

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.kotlin.mpp)
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
            cinterops.create("parser") {
                includeDirs.allHeaders(grammarDir.resolve("bindings/c"))
                extraOpts("-libraryPath", libsDir.dir(konanTarget.name))
            }
        }
    }

    jvmToolchain(17)

    sourceSets {
        commonMain {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            languageSettings {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }

            dependencies {
                implementation(libs.kotlin.stdlib)
            }
        }
    }
}

/*
android {
    namespace = "$group.$grammarName"
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
}

publishing {
    publications.withType(MavenPublication::class) {
        artifactId = "ktreesitter-$grammarName"
        artifact(tasks["javadocJar"])
        pom {
            name.set("KTreeSitter $grammarName")
            description.set("$grammarName grammar for KTreeSitter")
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

fun CInteropProcess.zigBuild(target: String, lib: String, vararg args: String) {
    exec {
        executable = "zig"
        workingDir = grammarDir
        args(
            "build-lib",
            "--name",
            "tree-sitter-$grammarName",
            "-lc",
            "-static",
            "-Isrc",
            "-target",
            target,
            "-cflags",
            "-std=c11",
            "--",
            "src/parser.c",
            // "src/scanner.c",
            *args
        )
    }

    copy {
        from(grammarDir.resolve(lib))
        into(libsDir.dir(konanTarget.name))
    }

    delete(grammarDir.resolve(lib))
}

if (os.isLinux) {
    tasks.getByName<CInteropProcess>("cinteropParserLinuxX64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter-$grammarName.a"))

        doFirst {
            zigBuild("x86_64-linux", "libtree-sitter-$grammarName.a")
        }
    }

    tasks.getByName<CInteropProcess>("cinteropParserLinuxArm64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter-$grammarName.a"))

        doFirst {
            zigBuild("aarch64-linux", "libtree-sitter-$grammarName.a")
        }
    }
} else if (os.isWindows) {
    tasks.getByName<CInteropProcess>("cinteropParserMingwX64") {
        outputs.file(libsDir.dir(konanTarget.name).file("tree-sitter-$grammarName.lib"))

        doFirst {
            zigBuild("x86_64-windows-gnu", "tree-sitter-$grammarName.lib")
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

    tasks.getByName<CInteropProcess>("cinteropParserMacosX64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter-$grammarName.a"))

        doFirst {
            val sysroot = findSysroot("macosx")
            zigBuild("x86_64-macos", "libtree-sitter-$grammarName.a", "--sysroot", sysroot)
        }
    }

    tasks.getByName<CInteropProcess>("cinteropParserMacosArm64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter-$grammarName.a"))

        doFirst {
            val sysroot = findSysroot("macosx")
            zigBuild("aarch64-macos", "libtree-sitter-$grammarName.a", "--sysroot", sysroot)
        }
    }

    tasks.getByName<CInteropProcess>("cinteropParserIosArm64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter-$grammarName.a"))

        doFirst {
            val sysroot = findSysroot("iphoneos")
            zigBuild("aarch64-ios", "libtree-sitter-$grammarName.a", "--sysroot", sysroot)
        }
    }

    tasks.getByName<CInteropProcess>("cinteropParserIosSimulatorArm64") {
        outputs.file(libsDir.dir(konanTarget.name).file("libtree-sitter-$grammarName.a"))

        doFirst {
            val sysroot = findSysroot("iphoneos")
            zigBuild("aarch64-ios-simulator", "libtree-sitter-$grammarName.a", "--sysroot", sysroot)
        }
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}
