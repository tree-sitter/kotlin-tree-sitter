package io.github.treesitter.ktreesitter

data class Range(
    val startPoint: Point,
    val endPoint: Point,
    val startByte: UInt,
    val endByte: UInt
) {
    init {
        require(startPoint <= endPoint) { "Invalid point range: $startPoint to $endPoint" }
        require(startByte <= endByte) { "Invalid byte range: $startByte to $endByte" }
    }
}
