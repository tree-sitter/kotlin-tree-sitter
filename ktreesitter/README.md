# KTreeSitter

Kotlin bindings to the [tree-sitter] parsing library.

## Supported platforms

- [x] JVM
- [x] Android
- [x] Native
- [ ] WASI

*JS and WASM JS will not be supported.*

## Installation

```groovy
dependencies {
    implementation("io.github.tree-sitter:ktreesitter") version $ktreesitterVersion
}

repositories {
    mavenCentral()
}
```

## Basic usage

```kotlin
import io.github.treesitter.ktreesitter.*
import io.github.treesitter.ktreesitter.java.TreeSitterJava

val language = Language(TreeSitterJava.language())
val parser = Parser(language)
val tree = parser.parse("class Foo {}")

assert(tree.rootNode.type == "program")
```

[tree-sitter]: https://tree-sitter.github.io/tree-sitter/
