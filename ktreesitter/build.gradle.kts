import com.android.build.api.dsl.LibraryExtension
import java.io.OutputStream.nullOutputStream
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.PlatformManager

inline val File.unixPath: String
    get() = if (!os.isWindows) path else path.replace(File.pathSeparatorChar, '/')

fun KotlinNativeTarget.treesitter() {
    val suffix = name.replaceFirstChar(Char::uppercaseChar)
    val buildDir = layout.buildDirectory.dir("cmake/${konanTarget.name}")
    val libDir = libsDir.dir(konanTarget.name).asFile

    val configureTreesitter = tasks.register<Exec>("configureTreesitter$suffix") {
        group = "build"
        description = "Configure the static library for ${konanTarget.name}"
        executable = "cmake"
        standardOutput = nullOutputStream()

        onlyIf { HostManager().isEnabled(konanTarget) }

        val konanHome = project.extra["konanHome"] as String
        inputs.property("konanHome", konanHome)
        inputs.file(treesitterDir.resolve("CMakeLists.txt"))
        outputs.file(buildDir.map { it.file("CMakeCache.txt") })

        doFirst {
            val platform = PlatformManager(konanHome).platform(konanTarget)
            val llvmDir = File(platform.absoluteLlvmHome, "bin")
            val cFlags = platform.clang.clangArgs + "-DTREE_SITTER_HIDE_SYMBOLS"
            val exe = if (os.isWindows) ".exe" else ""

            args("-S", treesitterDir.unixPath, "-B", buildDir.get().asFile.unixPath)
            args("-G", "Ninja", "-DCMAKE_BUILD_TYPE=RelWithDebInfo")
            args("-DCMAKE_C_COMPILER=" + llvmDir.resolve("clang$exe").unixPath)
            args("-DCMAKE_AR=" + llvmDir.resolve("llvm-ar$exe").unixPath)
            args("-DCMAKE_C_FLAGS=" + cFlags.joinToString(" "))
            args("-DBUILD_SHARED_LIBS=OFF", "-DAMALGAMATED=ON")
            args("-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY")
            args("-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=" + libDir.unixPath)
            if (konanTarget.family.isAppleFamily) {
                args("-DCMAKE_OSX_SYSROOT=" + platform.absoluteTargetSysRoot)
                args("-DCMAKE_OSX_DEPLOYMENT_TARGET=")
            }
        }
    }

    val buildTreesitter = tasks.register<Exec>("buildTreesitter$suffix") {
        group = "build"
        description = "Build the static library for ${konanTarget.name}"
        executable = "cmake"
        standardOutput = nullOutputStream()

        onlyIf { HostManager().isEnabled(konanTarget) }

        args("--build", buildDir.get().asFile.unixPath)

        inputs.files(configureTreesitter)
        inputs.files(treesitterDir.resolve("lib/src"), treesitterDir.resolve("lib/include"))
        outputs.file(libDir.resolve("libtree-sitter.a"))
    }

    tasks.withType<CInteropProcess>().configureEach {
        if (konanTarget == this@treesitter.konanTarget)
            inputs.files(buildTreesitter)
    }

    compilations.named("main") {
        cinterops.register("treesitter") {
            val srcDir = treesitterDir.resolve("lib/src")
            val includeDir = treesitterDir.resolve("lib/include")
            includeDirs.allHeaders(srcDir, includeDir)
            includeDirs.headerFilterOnly(includeDir)
            extraOpts("-libraryPath", libsDir.dir(konanTarget.name))
        }
    }
}

val os: OperatingSystem = OperatingSystem.current()
val libsDir = layout.buildDirectory.get().dir("libs")
val treesitterDir = rootDir.resolve("tree-sitter")
val jniBuildDir = layout.buildDirectory.dir("cmake/jni")
val jniLibDir = buildString {
    append("lib/")
    when {
        os.isLinux -> append("linux/")
        os.isMacOsX -> append("macos/")
        os.isWindows -> append("windows/")
        else -> throw GradleException("Unsupported operating system: $os")
    }
    when (val arch = System.getProperty("os.arch")) {
        "amd64", "x86_64" -> append("x64")
        "aarch64", "arm64" -> append("aarch64")
        else -> throw GradleException("Unsupported architecture: $arch")
    }
}

version = property("project.version") as String

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.kotlin.mpp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotest)
    alias(libs.plugins.ksp)
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

    linuxX64 { treesitter() }
    linuxArm64 { treesitter() }
    mingwX64 { treesitter() }
    macosArm64 { treesitter() }
    iosArm64 { treesitter() }
    iosSimulatorArm64 { treesitter() }

    applyDefaultHierarchyTemplate()

    jvmToolchain(17)

    sourceSets {
        commonMain {
            languageSettings {
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
                implementation(libs.kotest.symbolprocessor)
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

configure<LibraryExtension> {
    namespace = "io.github.treesitter.$name"
    compileSdk = (property("sdk.version.compile") as String).toInt()
    ndkVersion = property("ndk.version") as String
    defaultConfig {
        minSdk = (property("sdk.version.min") as String).toInt()
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += setOf("x86_64", "arm64-v8a", "armeabi-v7a")
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = property("cmake.version") as String
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.all {
            it.enabled = false
        }
        @Suppress("UnstableApiUsage")
        managedDevices {
            localDevices {
                create("emulator") {
                    device = "Pixel 2"
                    apiLevel = 33
                    systemImageSource = "aosp-atd"
                    testedAbi = "x86_64"
                    require64Bit = true
                }
            }
        }
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

tasks.register<Jar>("javadocJar") {
    group = "documentation"
    description = "Create javadoc JAR"
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

dokka {
    val tmpDir = layout.buildDirectory.get().dir("tmp")
    val ref = System.getenv("GITHUB_SHA")?.subSequence(0, 7) ?: "master"
    val url = "https://github.com/tree-sitter/kotlin-tree-sitter/blob/$ref/ktreesitter"

    moduleName.set("KTreeSitter")

    pluginsConfiguration.html {
        footerMessage = "© 2024 tree-sitter"
        homepageLink = "https://tree-sitter.github.io/tree-sitter/"
        customAssets.from(rootDir.resolve("gradle/logo-icon.svg"))
    }

    dokkaSourceSets.configureEach {
        jdkVersion.set(17)
        includes.from(tmpDir.file("README.md"))
        sourceLink {
            remoteUrl(url)
            localDirectory.set(projectDir)
        }
    }
}

tasks.withType<DokkaGeneratePublicationTask> {
    val tmpDir = layout.buildDirectory.get().dir("tmp")

    inputs.file("README.md")

    doFirst {
        copy {
            from(file("README.md"))
            into(tmpDir)
            filter { file ->
                file.replaceFirst("# KTreeSitter", "# Module KTreeSitter")
                    .replaceFirst($$"$ktreesitterVersion", """"$version"""")
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

val configureJniLibrary = tasks.register<Exec>("configureJniLibrary") {
    group = "build"
    description = "Configure the JNI library"
    executable = "cmake"
    standardOutput = nullOutputStream()

    args("-S", projectDir.unixPath, "-B", jniBuildDir.get().asFile.unixPath)
    args("-G", "Ninja", "-DCMAKE_BUILD_TYPE=RelWithDebInfo")
    args("-DCMAKE_INSTALL_PREFIX=" + projectDir.resolve("src/jvmMain/resources").unixPath)
    args("-DCMAKE_INSTALL_${if (os.isWindows) "BINDIR" else "LIBDIR"}=$jniLibDir")
    environment("JAVA_HOME", System.getProperty("java.home"))

    inputs.file("CMakeLists.txt")
    outputs.file(jniBuildDir.map { it.file("CMakeCache.txt") })
}

val buildJniLibrary = tasks.register<Exec>("buildJniLibrary") {
    group = "build"
    description = "Build the JNI library"
    executable = "cmake"
    standardOutput = nullOutputStream()

    args("--build", jniBuildDir.get().asFile.path, "--target", "install")

    inputs.files(configureJniLibrary)
    inputs.files("src/jni")
    inputs.files(treesitterDir.resolve("lib/src"), treesitterDir.resolve("lib/include"))
    outputs.dir(projectDir.resolve("src/jvmMain/resources").resolve(jniLibDir))
}

tasks.getByName("jvmProcessResources").mustRunAfter(buildJniLibrary)

tasks.getByName<Test>("jvmTest") {
    dependsOn(buildJniLibrary)
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
