package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.internal.*
import kotlinx.cinterop.*

@ExperimentalForeignApi
internal inline fun TSPoint.from(point: Point) = apply {
    row = point.row
    column = point.column
}

@ExperimentalForeignApi
internal inline fun TSRange.from(range: Range) = apply {
    start_point.from(range.startPoint)
    end_point.from(range.endPoint)
    start_byte = range.startByte
    end_byte = range.endByte
}

@ExperimentalForeignApi
internal inline fun TSPoint.convert() = Point(row, column)

@ExperimentalForeignApi
internal inline fun TSRange.convert() =
    Range(start_point.convert(), end_point.convert(), start_byte, end_byte)

@ExperimentalForeignApi
internal inline fun CValue<TSNode>.convert(tree: Tree) =
    if (ts_node_is_null(this)) null else Node(this, tree)

@ExperimentalForeignApi
internal inline val <reified T : CVariable> CValue<T>.ptr: CPointer<T>
    get() = place(kts_malloc(sizeOf<T>().convert())!!.reinterpret())
