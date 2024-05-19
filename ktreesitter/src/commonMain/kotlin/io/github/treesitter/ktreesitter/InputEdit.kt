package io.github.treesitter.ktreesitter

data class InputEdit(
    val startByte: UInt,
    val oldEndByte: UInt,
    val newEndByte: UInt,
    val startPoint: Point,
    val oldEndPoint: Point,
    val newEndPoint: Point
)
