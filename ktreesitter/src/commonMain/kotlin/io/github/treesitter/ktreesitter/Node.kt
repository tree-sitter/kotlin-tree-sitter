package io.github.treesitter.ktreesitter

expect class Node {
    internal val tree: Tree
    val id: ULong
    val symbol: UShort
    val grammarSymbol: UShort
    val type: String
    val grammarType: String
    val isNamed: Boolean
    val isExtra: Boolean
    val isError: Boolean
    val isMissing: Boolean
    val hasChanges: Boolean
    val hasError: Boolean
    val parseState: UShort
    val nextParseState: UShort
    val startByte: UInt
    val endByte: UInt
    val byteRange: UIntRange
    val range: Range
    val startPoint: Point
    val endPoint: Point
    val childCount: UInt
    val namedChildCount: UInt
    val descendantCount: UInt
    val parent: Node?
    val nextSibling: Node?
    val prevSibling: Node?
    val nextNamedSibling: Node?
    val prevNamedSibling: Node?
    val children: List<Node>
    val namedChildren: List<Node>

    fun child(index: UInt): Node?
    fun namedChild(index: UInt): Node?
    fun childByFieldId(id: UShort): Node?
    fun childByFieldName(name: String): Node?
    fun childrenByFieldId(id: UShort): List<Node>
    fun childrenByFieldName(name: String): List<Node>
    fun fieldNameForChild(index: UInt): String?
    fun descendant(start: UInt, end: UInt): Node?
    fun descendant(start: Point, end: Point): Node?
    fun namedDescendant(start: UInt, end: UInt): Node?
    fun namedDescendant(start: Point, end: Point): Node?
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

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}
