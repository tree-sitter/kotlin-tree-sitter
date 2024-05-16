package io.github.treesitter.ktreesitter.java

import io.github.treesitter.ktreesitter.java.internal.tree_sitter_java

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun language(): Any = tree_sitter_java()!!
