import java.io.OutputStream.nullOutputStream
import java.net.URI
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.support.useToRun
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.konan.target.PlatformManager

inline val File.unixPath: String
    get() = if (!os.isWindows) path else path.replace("\\", "/")

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

        getByName("androidInstrumentedTest") {
            dependencies {
                implementation(libs.bundles.kotest.core)
                implementation(libs.bundles.kotest.android)
                rootProject.project("languages").subprojects.forEach {
                    implementation(project(":languages:${it.name}"))
                }
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    packaging.resources {
        excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/licenses/ASM",
            "win32-x86-64/attach_hotspot_windows.dll",
            "win32-x86/attach_hotspot_windows.dll"
        )
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
            name = "KTreeSitter"
            description = "Kotlin bindings to the Tree-sitter parsing library"
            url = "https://tree-sitter.github.io/kotlin-tree-sitter/"
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

tasks.dokkaHtml {
    val tmpDir = layout.buildDirectory.get().dir("tmp")
    val ref = System.getenv("GITHUB_SHA")?.subSequence(0, 7) ?: "master"
    val url = "https://github.com/tree-sitter/kotlin-tree-sitter/blob/$ref/ktreesitter"

    inputs.file(file("README.md"))

    moduleName.set("KTreeSitter")

    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        footerMessage = "© 2024 tree-sitter"
        homepageLink = "https://tree-sitter.github.io/tree-sitter/"
        customAssets = listOf(rootDir.resolve("gradle/logo-icon.svg"))
    }

    dokkaSourceSets.configureEach {
        jdkVersion.set(17)
        noStdlibLink.set(true)
        includes.from(tmpDir.file("README.md"))
        externalDocumentationLink("https://kotlinlang.org/api/core/")
        sourceLink {
            localDirectory.set(projectDir)
            remoteUrl.set(URI.create(url).toURL())
        }
    }

    doFirst {
        copy {
            from(file("README.md"))
            into(tmpDir)
            filter { file ->
                file.replaceFirst("# KTreeSitter", "# Module KTreeSitter")
                    .replaceFirst("\$ktreesitterVersion", "\"$version\"")
                    .replace("[x]", "&#x2611;").replace("[ ]", "&#x2610;")
            }
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

    val runKonan = File(konanHome.get()).resolve("bin")
        .resolve(if (os.isWindows) "run_konan.bat" else "run_konan").path
    val libFile = libsDir.dir(konanTarget.name).file(
        "${konanTarget.family.staticPrefix}tree-sitter.${konanTarget.family.staticSuffix}"
    ).asFile
    val objectFile = treesitterDir.resolve("lib.o")
    val loader = PlatformManager(konanHome.get(), false, konanDataDir.orNull).loader(konanTarget)

    doFirst {
        if (!File(loader.absoluteTargetToolchain).isDirectory) loader.downloadDependencies()

        val argsFile = File.createTempFile("args", null)
        argsFile.deleteOnExit()
        argsFile.writer().useToRun {
            write("-I" + treesitterDir.resolve("lib/src").unixPath + "\n")
            write("-I" + treesitterDir.resolve("lib/include").unixPath + "\n")
            write("-DTREE_SITTER_HIDE_SYMBOLS\n")
            write("-fvisibility=hidden\n")
            write("-std=c11\n")
            write("-O2\n")
            write("-g\n")
            write("-c\n")
            write(treesitterDir.resolve("lib/src/lib.c").unixPath + "\n")
        }

        exec {
            executable = runKonan
            workingDir = treesitterDir
            standardOutput = nullOutputStream()
            args("clang", "clang", konanTarget.name, "@" + argsFile.path)
        }

        exec {
            executable = runKonan
            workingDir = treesitterDir
            standardOutput = nullOutputStream()
            args("llvm", "llvm-ar", "rcs", libFile.path, objectFile.path)
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
    systemProperty("gradle.build.dir", layout.buildDirectory.get().asFile.path)
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}
