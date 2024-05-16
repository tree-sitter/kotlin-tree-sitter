package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.internal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class TreeCursor private constructor(
    private val self: CValuesRef<TSTreeCursor>?,
    internal actual val tree: Tree
) {
    internal constructor(node: Node) : this(ts_tree_cursor_new(node.self), node.tree) {
        currentNode = node
    }

    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val cleaner = createCleaner(self, ::ts_tree_cursor_delete)

    actual var currentNode: Node? = null
        get() {
            if (field == null)
                field = ts_tree_cursor_current_node(self).convert(tree)
            return field
        }
        private set

    actual val currentDepth: UInt
        get() = ts_tree_cursor_current_depth(self)

    actual val currentFieldId: UShort?
        get() = ts_tree_cursor_current_field_id(self)

    actual val currentFieldName: String?
        get() = ts_tree_cursor_current_field_name(self)?.toKString()

    actual val currentDescendantIndex: UInt
        get() = ts_tree_cursor_current_descendant_index(self)

    actual fun copy() = TreeCursor(ts_tree_cursor_copy(self), tree)

    actual fun reset(node: Node) {
        ts_tree_cursor_reset(self, node.self)
        currentNode = null
    }

    actual fun reset(cursor: TreeCursor) {
        ts_tree_cursor_reset_to(self, cursor.self)
        currentNode = null
    }

    actual fun gotoFirstChild(): Boolean {
        val result = ts_tree_cursor_goto_first_child(self)
        if (result) currentNode = null
        return result
    }

    actual fun gotoLastChild(): Boolean {
        val result = ts_tree_cursor_goto_last_child(self)
        if (result) currentNode = null
        return result
    }

    actual fun gotoParent(): Boolean {
        val result = ts_tree_cursor_goto_parent(self)
        if (result) currentNode = null
        return result
    }

    actual fun gotoNextSibling(): Boolean {
        val result = ts_tree_cursor_goto_next_sibling(self)
        if (result) currentNode = null
        return result
    }

    actual fun gotoPreviousSibling(): Boolean {
        val result = ts_tree_cursor_goto_previous_sibling(self)
        if (result) currentNode = null
        return result
    }

    actual fun gotoDescendant(index: UInt) {
        ts_tree_cursor_goto_descendant(self, index)
        currentNode = null
    }

    actual fun gotoFirstChildForByte(byte: UInt): UInt? {
        val index = ts_tree_cursor_goto_first_child_for_byte(self, byte)
        if (index == -1L) return null
        currentNode = null
        return index.convert<UInt>()
    }

    actual fun gotoFirstChildForPoint(point: Point): UInt? {
        val pointValue = cValue<TSPoint> { from(point) }
        val index = ts_tree_cursor_goto_first_child_for_point(self, pointValue)
        if (index == -1L) return null
        currentNode = null
        return index.convert<UInt>()
    }
}
