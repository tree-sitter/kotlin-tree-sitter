package io.github.treesitter.ktreesitter

import cnames.structs.TSTree
import io.github.treesitter.ktreesitter.internal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.*

/** A class that represents a syntax tree. */
@OptIn(ExperimentalForeignApi::class)
actual class Tree internal constructor(
    internal val self: CPointer<TSTree>,
    private var source: ByteArray?,
    /** The language that was used to parse the syntax tree. */
    actual val language: Language,
    private val encoding: InputEncoding = InputEncoding.UTF_8
) {
    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val cleaner = createCleaner(self, ::ts_tree_delete)

    /** The root node of the syntax tree. */
    actual val rootNode = Node(ts_tree_root_node(self), this)

    /** The included ranges of the syntax tree. */
    actual val includedRanges by lazy {
        memScoped {
            val length = alloc<UIntVar>()
            val ranges = ts_tree_included_ranges(self, length.ptr) ?: return@lazy emptyList()
            val result = List(length.value.convert()) { ranges[it].convert() }
            kts_free(ranges)
            result
        }
    }

    /**
     * Get the root node of the syntax tree, but with
     * its position shifted forward by the given offset.
     */
    actual fun rootNodeWithOffset(bytes: UInt, extent: Point): Node? {
        val offsetExtent = cValue<TSPoint> { from(extent) }
        return ts_tree_root_node_with_offset(self, bytes, offsetExtent).convert(this)
    }

    /**
     * Edit the syntax tree to keep it in sync
     * with source code that has been modified.
     */
    actual fun edit(edit: InputEdit) {
        val inputEdit = cValue<TSInputEdit> { from(edit) }
        ts_tree_edit(self, inputEdit)
        source = null
    }

    /**
     * Create a shallow copy of the syntax tree.
     *
     * You need to copy a syntax tree in order to use it on multiple
     * threads or coroutines, as syntax trees are not thread safe.
     */
    actual fun copy() = Tree(ts_tree_copy(self)!!, source, language, encoding)

    /** Create a new tree cursor starting from the node of the tree. */
    actual fun walk() = TreeCursor(rootNode)

    /** Get the source code of the syntax tree, if available. */
    actual fun text(): CharSequence? = source?.let { encoding.decode(it, 0, it.size) }

    /**
     * Compare an old edited syntax tree to a new
     * syntax tree representing the same document.
     *
     * For this to work correctly, this tree must have been
     * edited such that its ranges match up to the new tree.
     *
     * @return A list of ranges whose syntactic structure has changed.
     */
    actual fun changedRanges(newTree: Tree): List<Range> = memScoped {
        val length = alloc<UIntVar>()
        val ranges = ts_tree_get_changed_ranges(self, newTree.self, length.ptr)
        if (length.value == 0U || ranges == null) return emptyList()
        val result = List(length.value.convert()) { ranges[it].convert() }
        kts_free(ranges)
        return result
    }

    override fun toString() = "Tree(language=$language, source=${text()})"

    internal fun hasText() = source != null

    internal fun slice(startByte: UInt, endByte: UInt) = source?.let {
        val start = startByte.toInt().coerceIn(0, it.size)
        val end = endByte.toInt().coerceIn(start, it.size)
        encoding.decode(it, start, end)
    }
}
