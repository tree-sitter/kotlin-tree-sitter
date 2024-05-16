package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.internal.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class Node internal constructor(
    internal val self: CValue<TSNode>,
    internal actual val tree: Tree
) {
    private val source = tree.source

    actual val id: ULong = kts_node_id(self)

    actual val symbol: UShort
        get() = ts_node_symbol(self)

    actual val grammarSymbol: UShort
        get() = ts_node_grammar_symbol(self)

    actual val type: String
        get() = ts_node_type(self)!!.toKString()

    actual val grammarType: String
        get() = ts_node_grammar_type(self)!!.toKString()

    actual val isNamed: Boolean
        get() = ts_node_is_named(self)

    actual val isExtra: Boolean
        get() = ts_node_is_extra(self)

    actual val isError: Boolean
        get() = ts_node_is_error(self)

    actual val isMissing: Boolean
        get() = ts_node_is_missing(self)

    actual val hasChanges: Boolean
        get() = ts_node_has_changes(self)

    actual val hasError: Boolean
        get() = ts_node_has_error(self)

    actual val parseState: UShort
        get() = ts_node_parse_state(self)

    actual val nextParseState: UShort
        get() = ts_node_next_parse_state(self)

    actual val startByte: UInt
        get() = ts_node_start_byte(self)

    actual val endByte: UInt
        get() = ts_node_end_byte(self)

    actual val byteRange: UIntRange
        get() = startByte..endByte

    actual val range: Range
        get() = kts_node_range(self).useContents(TSRange::convert)

    actual val startPoint: Point
        get() = ts_node_start_point(self).useContents(TSPoint::convert)

    actual val endPoint: Point
        get() = ts_node_end_point(self).useContents(TSPoint::convert)

    actual val childCount: UInt
        get() = ts_node_child_count(self)

    actual val namedChildCount: UInt
        get() = ts_node_named_child_count(self)

    actual val descendantCount: UInt
        get() = ts_node_descendant_count(self)

    actual val parent: Node?
        get() = ts_node_parent(self).convert(tree)

    actual val nextSibling: Node?
        get() = ts_node_next_sibling(self).convert(tree)

    actual val prevSibling: Node?
        get() = ts_node_prev_sibling(self).convert(tree)

    actual val nextNamedSibling: Node?
        get() = ts_node_next_named_sibling(self).convert(tree)

    actual val prevNamedSibling: Node?
        get() = ts_node_prev_named_sibling(self).convert(tree)

    actual val children: List<Node> by lazy {
        val length = childCount.toInt()
        if (length == 0) return@lazy emptyList()
        val cursor = ts_tree_cursor_new(self)
        val children = List(length) {
            val node = ts_tree_cursor_current_node(cursor)
            ts_tree_cursor_goto_next_sibling(cursor)
            Node(node, tree)
        }
        ts_tree_cursor_delete(cursor)
        children
    }

    actual val namedChildren: List<Node> by lazy {
        val length = namedChildCount.toInt()
        if (length == 0) return@lazy emptyList()
        val children = ArrayList<Node>(length)
        val cursor = ts_tree_cursor_new(self)
        do {
            val node = ts_tree_cursor_current_node(cursor)
            if (ts_node_is_named(node))
                children += Node(node, tree)
        } while (ts_tree_cursor_goto_next_sibling(cursor))
        ts_tree_cursor_delete(cursor)
        children.apply { trimToSize() }
    }

    actual fun child(index: UInt): Node? {
        if (index < childCount) return ts_node_child(self, index).convert(tree)
        throw IndexOutOfBoundsException("Child index $index is out of bounds")
    }

    actual fun namedChild(index: UInt): Node? {
        if (index < namedChildCount) return ts_node_named_child(self, index).convert(tree)
        throw IndexOutOfBoundsException("Child index $index is out of bounds")
    }

    actual fun childByFieldId(id: UShort) = ts_node_child_by_field_id(self, id).convert(tree)

    actual fun childByFieldName(name: String) =
        ts_node_child_by_field_name(self, name, name.length.convert()).convert(tree)

    actual fun childrenByFieldId(id: UShort): List<Node> {
        if (id == UShort.MIN_VALUE) return emptyList()
        val length = childCount.toInt()
        if (length == 0) return emptyList()
        val children = ArrayList<Node>(length)
        val cursor = ts_tree_cursor_new(self)
        var ok = ts_tree_cursor_goto_first_child(cursor)
        while (ok) {
            if (ts_tree_cursor_current_field_id(cursor) == id)
                children += Node(ts_tree_cursor_current_node(cursor), tree)
            ok = ts_tree_cursor_goto_next_sibling(cursor)
        }
        ts_tree_cursor_delete(cursor)
        return children.apply { trimToSize() }
    }

    actual fun childrenByFieldName(name: String): List<Node> {
        val lang = ts_tree_language(tree.self)
        val id = ts_language_field_id_for_name(lang, name, name.length.convert())
        return childrenByFieldId(id)
    }

    actual fun fieldNameForChild(index: UInt): String? {
        if (index < childCount) return ts_node_field_name_for_child(self, index)?.toKString()
        throw IndexOutOfBoundsException("Child index $index is out of bounds")
    }

    actual fun descendant(start: UInt, end: UInt) =
        ts_node_descendant_for_byte_range(self, start, end).convert(tree)

    actual fun descendant(start: Point, end: Point): Node? {
        val startPoint = cValue<TSPoint> { from(start) }
        val endPoint = cValue<TSPoint> { from(end) }
        return ts_node_descendant_for_point_range(self, startPoint, endPoint).convert(tree)
    }

    actual fun namedDescendant(start: UInt, end: UInt) =
        ts_node_named_descendant_for_byte_range(self, start, end).convert(tree)

    actual fun namedDescendant(start: Point, end: Point): Node? {
        val startPoint = cValue<TSPoint> { from(start) }
        val endPoint = cValue<TSPoint> { from(end) }
        return ts_node_named_descendant_for_point_range(self, startPoint, endPoint).convert(tree)
    }

    actual fun walk() = TreeCursor(this)

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
        ts_node_edit(self, edit)
    }

    actual fun text() =
        source?.subSequence(startByte.toInt(), minOf(endByte.toInt(), source.length))

    actual override fun equals(other: Any?) =
        this === other || (other is Node && ts_node_eq(self, other.self))

    actual override fun hashCode() = kts_node_hash(self)

    actual override fun toString() = ts_node_string(self)?.toKString() ?: ""
}