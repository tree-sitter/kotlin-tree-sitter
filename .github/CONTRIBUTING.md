# Contributing

## Commits

Commits must follow the [Conventional Commits] specification.
If you're developing on IntelliJ IDEA or Android Studio,
you can use the [Conventional Commit plugin].

[Conventional Commits]: https://www.conventionalcommits.org/en/v1.0.0/
[Conventional Commit plugin]: https://plugins.jetbrains.com/plugin/13389-conventional-commit

## Prerequisites

- JDK 17+
- Android SDK & NDK
- CMake
- C compiler

## Building

### Clone the repository

```shell
git clone https://github.com/tree-sitter/kotlin-tree-sitter
cd kotlin-tree-sitter
```

### Run the tests

#### JVM

```shell
./gradlew :ktreesitter:jvmTest
```

#### Android

```shell
./gradlew :ktreesitter:emulatorDebugAndroidTest
```

#### Native

Linux:

```shell
./gradlew :ktreesitter:linuxX64Test
```

macOS:

```shell
# x64
./gradlew :ktreesitter:macosX64Test
# arm64
./gradlew :ktreesitter:macosArm64Test
```

Windows:

```shell
./gradlew :ktreesitter:mingwX64Test
```

## Linting

Code linting is performed using [Ktlint] and [detekt].
Configuration for IntelliJ IDEA and Android Studio is included
(requires the [Ktlint][Ktlint plugin] and [detekt][detekt plugin] plugins).

[Ktlint]: https://pinterest.github.io/ktlint/latest/
[detekt]: https://detekt.dev/
[Ktlint plugin]: https://plugins.jetbrains.com/plugin/15057-ktlint
[detekt plugin]: https://plugins.jetbrains.com/plugin/10761-detekt
