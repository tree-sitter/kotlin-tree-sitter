package io.github.treesitter.ktreesitter

data class Point(val row: UInt, val column: UInt) : Comparable<Point> {
    override operator fun compareTo(other: Point): Int {
        val rowDiff = row.compareTo(other.row)
        if (rowDiff != 0) return rowDiff
        return column.compareTo(other.column)
    }
}
