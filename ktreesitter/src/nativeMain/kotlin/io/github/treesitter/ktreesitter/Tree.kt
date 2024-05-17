package io.github.treesitter.ktreesitter

import cnames.structs.TSTree
import io.github.treesitter.ktreesitter.internal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class Tree internal constructor(
    internal val self: CPointer<TSTree>?,
    internal actual var source: String?
) {
    init {
        checkNotNull(self) { "Parsing failed" }
    }

    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val cleaner = createCleaner(self, ::ts_tree_delete)

    actual val rootNode: Node
        get() = Node(ts_tree_root_node(self), this)

    actual val includedRanges: List<Range>
        get() = memScoped {
            val length = alloc<UIntVar>()
            val ranges = ts_tree_included_ranges(self, length.ptr) ?: return emptyList()
            val result = List(length.value.convert()) { ranges[it].convert() }
            kts_free(ranges)
            return result
        }

    actual fun rootNodeWithOffset(bytes: UInt, extent: Point): Node? {
        val offsetExtent = cValue<TSPoint> { from(extent) }
        return ts_tree_root_node_with_offset(self, bytes, offsetExtent).convert(this)
    }

    actual fun edit(
        startByte: UInt,
        oldEndByte: UInt,
        newEndByte: UInt,
        startPoint: Point,
        oldEndPoint: Point,
        newEndPoint: Point
    ) {
        val edit = cValue<TSInputEdit> {
            start_byte = startByte
            old_end_byte = oldEndByte
            new_end_byte = newEndByte
            start_point.from(startPoint)
            old_end_point.from(oldEndPoint)
            new_end_point.from(newEndPoint)
        }
        ts_tree_edit(self, edit)
        source = null
    }

    actual fun walk() = TreeCursor(rootNode)

    actual fun text(): CharSequence? = source

    actual fun changedRanges(newTree: Tree): List<Range> = memScoped {
        val length = alloc<UIntVar>()
        val ranges = ts_tree_get_changed_ranges(self, newTree.self, length.ptr)
        if (length.value == 0U || ranges == null) return emptyList()
        val result = List(length.value.convert()) { ranges[it].convert() }
        kts_free(ranges)
        return result
    }
}
