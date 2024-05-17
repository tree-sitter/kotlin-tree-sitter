package io.github.treesitter.ktreesitter

expect class TreeCursor {
    internal val tree: Tree

    var currentNode: Node?
        private set
    val currentDepth: UInt
    val currentFieldId: UShort
    val currentFieldName: String?
    val currentDescendantIndex: UInt

    fun copy(): TreeCursor
    fun reset(node: Node)
    fun reset(cursor: TreeCursor)
    fun gotoFirstChild(): Boolean
    fun gotoLastChild(): Boolean
    fun gotoParent(): Boolean
    fun gotoNextSibling(): Boolean
    fun gotoPreviousSibling(): Boolean
    fun gotoDescendant(index: UInt)
    fun gotoFirstChildForByte(byte: UInt): UInt?
    fun gotoFirstChildForPoint(point: Point): UInt?
}
