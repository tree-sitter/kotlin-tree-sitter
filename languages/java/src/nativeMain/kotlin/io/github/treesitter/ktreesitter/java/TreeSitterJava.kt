package io.github.treesitter.ktreesitter.java

import io.github.treesitter.ktreesitter.java.internal.tree_sitter_java
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual object TreeSitterJava {
    actual fun language(): Any = tree_sitter_java()!!
}
