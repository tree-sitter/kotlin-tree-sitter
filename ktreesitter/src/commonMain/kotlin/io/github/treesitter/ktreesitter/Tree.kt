package io.github.treesitter.ktreesitter

expect class Tree {
    internal var source: String?
    val rootNode: Node
    val includedRanges: List<Range>

    fun rootNodeWithOffset(bytes: UInt, extent: Point): Node?
    fun edit(
        startByte: UInt,
        oldEndByte: UInt,
        newEndByte: UInt,
        startPoint: Point,
        oldEndPoint: Point,
        newEndPoint: Point
    )
    fun walk(): TreeCursor
    fun text(): CharSequence?
    fun changedRanges(newTree: Tree): List<Range>
}
