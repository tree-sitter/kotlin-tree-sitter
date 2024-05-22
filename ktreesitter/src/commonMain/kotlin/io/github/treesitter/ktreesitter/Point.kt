package io.github.treesitter.ktreesitter

data class Point(val row: UInt, val column: UInt) : Comparable<Point> {
    override operator fun compareTo(other: Point): Int {
        val rowDiff = row.compareTo(other.row)
        if (rowDiff != 0) return rowDiff
        return column.compareTo(other.column)
    }

    companion object {
        val MIN = Point(UInt.MIN_VALUE, UInt.MIN_VALUE)
        val MAX = Point(UInt.MAX_VALUE, UInt.MAX_VALUE)
    }
}
