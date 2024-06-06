import java.io.OutputStream.nullOutputStream
import java.net.URL
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.konan.target.PlatformManager

val os: OperatingSystem = OperatingSystem.current()
val libsDir = layout.buildDirectory.get().dir("libs")
val treesitterDir = rootDir.resolve("tree-sitter")

version = property("project.version") as String

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.kotlin.mpp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotest)
    alias(libs.plugins.dokka)
}

buildscript {
    dependencies {
        classpath(libs.dokka.base)
    }
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
                    freeCompilerArgs.addAll("-Xexpect-actual-classes")
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

        jvmTest {
            dependencies {
                implementation(libs.bundles.kotest.junit)
            }
        }

        getByName("androidUnitTest") {
            dependencies {
                implementation(libs.kotest.junit.runner)
            }
        }
    }
}

android {
    namespace = "io.github.treesitter.$name"
    compileSdk = (property("sdk.version.compile") as String).toInt()
    ndkVersion = property("ndk.version") as String
    defaultConfig {
        minSdk = (property("sdk.version.min") as String).toInt()
        ndk {
            moduleName = "ktreesitter"
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
    testOptions.unitTests.all {
        it.useJUnitPlatform()
    }
    buildFeatures {
        resValues = false
    }
}

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
    val tmpDir = layout.buildDirectory.get().dir("tmp")
    val readme = rootDir.resolve("README.md")
    val url = "https://github.com/tree-sitter/kotlin-tree-sitter/blob/master/ktreesitter"

    inputs.file(readme)

    moduleName.set("KTreeSitter")

    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        footerMessage = "© 2024 tree-sitter"
        homepageLink = "https://tree-sitter.github.io/tree-sitter/"
        customAssets = listOf(rootDir.resolve("gradle/logo-icon.svg"))
    }

    dokkaSourceSets.configureEach {
        jdkVersion.set(17)
        noStdlibLink.set(true)
        // TODO: uncomment when ready
        // includes.from(tmpDir.file("README.md"))
        externalDocumentationLink("https://kotlinlang.org/api/core/")
        sourceLink {
            localDirectory.set(projectDir)
            remoteUrl.set(URL(url))
        }
    }

    doFirst {
        copy {
            from(readme)
            into(tmpDir)
            filter { it.replaceFirst("# KTreeSitter", "# Module KTreeSitter") }
        }
    }

    doLast {
        delete(tmpDir.file("README.md"))
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xlambdas=indy")
    }
}

tasks.withType<CInteropProcess>().configureEach {
    if (name.startsWith("cinteropTest")) return@configureEach

    val runKonan = File(konanHome.get()).resolve("bin/run_konan").path
    val libFile = libsDir.dir(konanTarget.name).file(
        "${konanTarget.family.staticPrefix}tree-sitter.${konanTarget.family.staticSuffix}"
    )
    val objectFile = treesitterDir.resolve("lib.o")
    val loader = PlatformManager(konanHome.get(), false, konanDataDir.orNull).loader(konanTarget)

    doFirst {
        if (!File(loader.absoluteTargetToolchain).isDirectory) loader.downloadDependencies()

        exec {
            executable = runKonan
            workingDir = treesitterDir
            standardOutput = nullOutputStream()
            args(
                "clang",
                "clang",
                konanTarget.name,
                "--sysroot", loader.absoluteTargetSysRoot,
                "-I", treesitterDir.resolve("lib/src"),
                "-I", treesitterDir.resolve("lib/include"),
                "-DTREE_SITTER_HIDE_SYMBOLS",
                "-fvisibility=hidden",
                "-std=c11",
                "-O2",
                "-g",
                "-c",
                treesitterDir.resolve("lib/src/lib.c")
            )
        }

        exec {
            executable = runKonan
            workingDir = treesitterDir
            standardOutput = nullOutputStream()
            args("llvm", "llvm-ar", "rcs", libFile, objectFile)
        }
    }

    outputs.file(libFile)
}

tasks.getByName<Test>("jvmTest") {
    useJUnitPlatform()
    reports.junitXml.apply {
        required.set(true)
        outputLocation.set(layout.buildDirectory.dir("reports/xml"))
    }
    val libraryPath = buildString {
        append(file(".cmake/build").path)
        val sep = if (os.isWindows) ";" else ":"
        rootProject.project("languages").subprojects.joinTo(this, sep, sep) {
            it.file(".cmake/build").path
        }
    }
    systemProperty("java.library.path", libraryPath)
    systemProperty("gradle.build.dir", layout.buildDirectory.get().asFile.path)
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}

gradle.taskGraph.whenReady {
    tasks.getByName<Test>("testDebugUnitTest") {
        val buildTasks = rootProject.subprojects.mapNotNull {
            it.tasks.findByName("buildCMakeDebug[x86_64]")
        }
        val sep = if (os.isWindows) ";" else ":"
        val path = buildTasks.joinToString(sep) {
            it.outputs.files.singleFile.path
        }
        systemProperty("java.library.path", path)
        val toolchain = android.ndkDirectory.resolve(
            "toolchains/llvm/prebuilt/linux-x86_64/sysroot" +
                "/usr/lib/x86_64-linux-android/${android.compileSdk}"
        )
        assert(toolchain.isDirectory) { "$toolchain is not a directory" }
        environment("LD_LIBRARY_PATH", toolchain.path)
    }
}
