import com.android.build.api.dsl.LibraryExtension
import java.io.OutputStream.nullOutputStream
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.PlatformManager

inline val File.unixPath: String
    get() = if (!os.isWindows) path else path.replace(File.pathSeparatorChar, '/')

fun KotlinNativeTarget.treesitterJava() {
    val suffix = name.replaceFirstChar(Char::uppercaseChar)
    val buildDir = layout.buildDirectory.dir("cmake/${konanTarget.name}")
    val libDir = libsDir.dir(konanTarget.name).asFile

    val configureTreesitterJava = tasks.register<Exec>("configureTreesitterJava$suffix") {
        group = "build"
        description = "Configure the static library for ${konanTarget.name}"
        executable = "cmake"
        standardOutput = nullOutputStream()

        onlyIf { HostManager().isEnabled(konanTarget) }

        val konanHome = project.extra["konanHome"] as String
        inputs.property("konanHome", konanHome)
        inputs.file(grammarDir.resolve("CMakeLists.txt"))
        outputs.file(buildDir.map { it.file("CMakeCache.txt") })

        doFirst {
            val platform = PlatformManager(konanHome).platform(konanTarget)
            val llvmDir = File(platform.absoluteLlvmHome, "bin")
            val cFlags = platform.clang.clangArgs + "-DTREE_SITTER_HIDE_SYMBOLS"
            val exe = if (os.isWindows) ".exe" else ""

            args("-S", grammarDir.unixPath, "-B", buildDir.get().asFile.unixPath, "-G", "Ninja")
            args("-DCMAKE_C_COMPILER=" + llvmDir.resolve("clang$exe").unixPath)
            args("-DCMAKE_AR=" + llvmDir.resolve("llvm-ar$exe").unixPath)
            args("-DCMAKE_C_FLAGS=" + cFlags.joinToString(" "))
            args("-DCMAKE_BUILD_TYPE=RelWithDebInfo", "-DBUILD_SHARED_LIBS=OFF")
            args("-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY")
            args("-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=" + libDir.unixPath)
            args("-DTREE_SITTER_CLI:STRING=" + if (os.isWindows) "rem" else "true")
            if (konanTarget.family.isAppleFamily) {
                args("-DCMAKE_OSX_SYSROOT=" + platform.absoluteTargetSysRoot)
                args("-DCMAKE_OSX_DEPLOYMENT_TARGET=")
            }
        }
    }

    val buildTreesitterJava = tasks.register<Exec>("buildTreesitterJava$suffix") {
        group = "build"
        description = "Build the static library for ${konanTarget.name}"

        onlyIf { HostManager().isEnabled(konanTarget) }

        executable = "cmake"
        args("--build", buildDir.get().asFile.unixPath)
        if (os.isWindows) args("--", "--quiet") else standardOutput = nullOutputStream()

        inputs.files(configureTreesitterJava)
        inputs.files(fileTree(grammarDir.resolve("src")) { include("*.c", "**/*.h") })
        outputs.file(libDir.resolve("libtree-sitter-${grammar.grammarName.get()}.a"))
    }

    tasks.withType<CInteropProcess>().configureEach {
        if (konanTarget == this@treesitterJava.konanTarget)
            inputs.files(buildTreesitterJava)
    }

    compilations.named("main") {
        cinterops.create("treesitterJava") {
            definitionFile.set(generateTask.interopFile)
            includeDirs.allHeaders(grammarDir.resolve("bindings/c"))
            extraOpts("-libraryPath", libsDir.dir(konanTarget.name))
        }
    }
}

val os: OperatingSystem = OperatingSystem.current()
val libsDir = layout.buildDirectory.get().dir("libs")
val grammarDir = projectDir.resolve("tree-sitter-java")

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

version = grammarDir.resolve("Makefile").readLines()
    .first { it.startsWith("VERSION := ") }.removePrefix("VERSION := ")

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.kotlin.mpp)
    alias(libs.plugins.android.library)
    id("io.github.tree-sitter.ktreesitter-plugin")
}

grammar {
    baseDir = grammarDir
    grammarName = project.name
    className = "TreeSitterJava"
    packageName = "io.github.treesitter.ktreesitter.java"
}

val generateTask = tasks.generateGrammarFiles.get()

kotlin {
    jvm {}

    androidTarget {
        withSourcesJar(true)
        publishLibraryVariants("release")
    }

    linuxX64 { treesitterJava() }
    linuxArm64 { treesitterJava() }
    mingwX64 { treesitterJava() }
    macosArm64 { treesitterJava() }
    iosArm64 { treesitterJava() }
    iosSimulatorArm64 { treesitterJava() }

    applyDefaultHierarchyTemplate()

    jvmToolchain(17)

    sourceSets {
        configureEach {
            kotlin.srcDir(generateTask.generatedSrc.dir("$name/kotlin"))
        }

        commonMain {
            languageSettings {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }

            dependencies {
                implementation(libs.kotlin.stdlib)
            }
        }

        jvmMain {
            resources.srcDir(generateTask.generatedSrc.dir("$name/resources"))
        }
    }
}

configure<LibraryExtension> {
    namespace = "io.github.treesitter.ktreesitter.${grammar.grammarName.get()}"
    compileSdk = (property("sdk.version.compile") as String).toInt()
    ndkVersion = property("ndk.version") as String
    defaultConfig {
        minSdk = (property("sdk.version.min") as String).toInt()
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += setOf("x86_64", "arm64-v8a", "armeabi-v7a")
        }
        resValue("string", "version", version as String)
    }
    buildFeatures {
        resValues = true
    }
    externalNativeBuild {
        cmake {
            path = generateTask.cmakeListsFile.get().asFile
            version = property("cmake.version") as String
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.matching { it.name.startsWith("configureCMake") }.configureEach {
    dependsOn(generateTask)
}

val configureJniLibrary = tasks.register<Exec>("configureJniLibrary") {
    group = "build"
    description = "Configure the JNI library"
    executable = "cmake"
    standardOutput = nullOutputStream()

    val prefix = generateTask.generatedSrc.get().dir("jvmMain/resources").asFile.unixPath
    args("-S", generateTask.cmakeListsFile.get().asFile.parentFile.unixPath)
    args("-B", jniBuildDir.get().asFile.unixPath, "-G", "Ninja")
    args("-DCMAKE_BUILD_TYPE=RelWithDebInfo", "-DCMAKE_INSTALL_PREFIX=$prefix")
    args("-DCMAKE_INSTALL_${if (os.isWindows) "BINDIR" else "LIBDIR"}=$jniLibDir")
    environment("JAVA_HOME", System.getProperty("java.home"))

    inputs.file(generateTask.cmakeListsFile)
    outputs.file(jniBuildDir.map { it.file("CMakeCache.txt") })
}

val buildJniLibrary = tasks.register<Exec>("buildJniLibrary") {
    group = "build"
    description = "Build the JNI library"
    executable = "cmake"
    standardOutput = nullOutputStream()

    args("--build", jniBuildDir.get().asFile.path, "--target", "install")

    inputs.files(configureJniLibrary)
    inputs.dir(generateTask.generatedSrc.dir("jni"))
    inputs.dir(grammarDir.resolve("src"))
    outputs.dir(
        generateTask.generatedSrc.get().dir("jvmMain/resources").asFile.resolve(jniLibDir)
    )
}

tasks.getByName("jvmProcessResources").mustRunAfter(buildJniLibrary)

tasks.getByName("jvmJar").dependsOn(buildJniLibrary)

tasks.register<Jar>("javadocJar") {
    group = "documentation"
    description = "Create javadoc JAR"
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType(MavenPublication::class) {
        val grammarName = grammar.grammarName.get()
        artifactId = grammar.libraryName.get()
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

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}
