import java.io.OutputStream.nullOutputStream
import org.apache.commons.io.FilenameUtils.separatorsToUnix
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.konan.target.PlatformManager

inline val File.unixPath: String
    get() = separatorsToUnix(path)

val os: OperatingSystem = OperatingSystem.current()
val libsDir = layout.buildDirectory.get().dir("libs")
val grammarDir = projectDir.resolve("tree-sitter-java")
val grammarName = project.name
val grammarFiles = arrayOf(
    // grammarDir.resolve("src/scanner.c"),
    grammarDir.resolve("src/parser.c")
)

version = grammarDir.resolve("Makefile").readLines()
    .first { it.startsWith("VERSION := ") }.removePrefix("VERSION := ")

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.kotlin.mpp)
    alias(libs.plugins.android.library)
}

kotlin {
    jvm {}

    androidTarget {
        withSourcesJar(true)
        publishLibraryVariants("release")
    }

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

android {
    namespace = "io.github.treesitter.ktreesitter.$grammarName"
    compileSdk = (property("sdk.version.compile") as String).toInt()
    ndkVersion = property("ndk.version") as String
    defaultConfig {
        minSdk = (property("sdk.version.min") as String).toInt()
        ndk {
            moduleName = "ktreesitter-java"
            //noinspection ChromeOsAbiSupport
            abiFilters += setOf("x86_64", "arm64-v8a", "armeabi-v7a")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            buildStagingDirectory = file(".cmake")
            version = property("cmake.version") as String
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

tasks.withType<CInteropProcess>().configureEach {
    if (name.startsWith("cinteropTest")) return@configureEach

    val runKonan = file(konanHome.get()).resolve("bin/run_konan").unixPath
    val libFile = libsDir.dir(konanTarget.name).file(
        konanTarget.family.staticPrefix +
            "tree-sitter-$grammarName." +
            konanTarget.family.staticSuffix
    ).asFile
    val objectFiles = grammarFiles.map {
        grammarDir.resolve(it.nameWithoutExtension + ".o").unixPath
    }.toTypedArray()
    val loader = PlatformManager(konanHome.get(), false, konanDataDir.orNull).loader(konanTarget)

    doFirst {
        if (!File(loader.absoluteTargetToolchain).isDirectory) loader.downloadDependencies()

        exec {
            executable = runKonan
            workingDir = grammarDir
            standardOutput = nullOutputStream()
            args(
                "clang",
                "clang",
                konanTarget.name,
                "--sysroot", separatorsToUnix(loader.absoluteTargetSysRoot),
                "-I", grammarDir.resolve("src").unixPath,
                "-DTREE_SITTER_HIDE_SYMBOLS",
                "-std=c11",
                "-O2",
                "-g",
                "-c",
                *grammarFiles.map { it.unixPath }.toTypedArray()
            )
        }

        exec {
            executable = runKonan
            workingDir = grammarDir
            standardOutput = nullOutputStream()
            args("llvm", "llvm-ar", "rcs", libFile.unixPath, *objectFiles)
        }
    }

    inputs.files(*grammarFiles)
    outputs.file(libFile)
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}
