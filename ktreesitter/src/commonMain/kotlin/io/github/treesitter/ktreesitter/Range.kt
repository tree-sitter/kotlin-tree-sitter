package io.github.treesitter.ktreesitter

/**
 * A range of positions in a text document,
 * both in terms of bytes and of row-column points.
 *
 * @constructor
 * @throws [IllegalArgumentException]
 *  If the end point is smaller than the start point,
 *  or the end byte is smaller than the start byte.
 */
data class Range @Throws(IllegalArgumentException::class) constructor(
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
