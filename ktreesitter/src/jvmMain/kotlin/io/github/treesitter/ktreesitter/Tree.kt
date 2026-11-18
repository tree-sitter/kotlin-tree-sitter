package io.github.treesitter.ktreesitter

/** A class that represents a syntax tree. */
actual class Tree internal constructor(
    private val self: Long,
    private var source: ByteArray?,
    /** The language that was used to parse the syntax tree. */
    actual val language: Language,
    private val encoding: InputEncoding = InputEncoding.UTF_8
) {
    init {
        RefCleaner(this, CleanAction(self))
    }

    /** The root node of the syntax tree. */
    actual val rootNode: Node
        external get

    /** The included ranges that were used to parse the syntax tree. */
    actual val includedRanges by lazy { nativeIncludedRanges() }

    /**
     * Get the root node of the syntax tree, but with
     * its position shifted forward by the given offset.
     */
    @JvmName("rootNodeWithOffset")
    actual external fun rootNodeWithOffset(bytes: UInt, extent: Point): Node?

    /**
     * Edit the syntax tree to keep it in sync
     * with source code that has been modified.
     */
    actual external fun edit(edit: InputEdit)

    /**
     * Create a shallow copy of the syntax tree.
     *
     * You need to copy a syntax tree in order to use it on multiple
     * threads or coroutines, as syntax trees are not thread safe.
     */
    actual fun copy() = Tree(copy(self), source, language, encoding)

    /** Create a new tree cursor starting from the node of the tree. */
    actual fun walk() = TreeCursor(rootNode)

    /** Get the source code of the syntax tree, if available. */
    actual fun text(): CharSequence? = source?.let { String(it, encoding.charset) }

    /**
     * Compare an old edited syntax tree to a new
     * syntax tree representing the same document.
     *
     * For this to work correctly, this tree must have been
     * edited such that its ranges match up to the new tree.
     *
     * @return A list of ranges whose syntactic structure has changed.
     */
    actual external fun changedRanges(newTree: Tree): List<Range>

    override fun toString() = "Tree(language=$language, source=${text()})"

    internal fun hasText() = source != null

    internal fun slice(startByte: UInt, endByte: UInt) = source?.let {
        val start = startByte.toInt().coerceIn(0, it.size)
        val end = endByte.toInt().coerceIn(start, it.size)
        String(it, start, end - start, encoding.charset)
    }

    private external fun nativeIncludedRanges(): List<Range>

    private class CleanAction(private val ptr: Long) : Runnable {
        override fun run() = delete(ptr)
    }

    private companion object {
        @JvmStatic
        private external fun copy(self: Long): Long

        @JvmStatic
        private external fun delete(self: Long)
    }
}
