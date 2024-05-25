package io.github.treesitter.ktreesitter

/** Any error that occurred while instantiating a [Query]. */
@Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
sealed class QueryError : Throwable() {
    abstract override val message: String

    /** A query syntax error. */
    class Syntax(val row: UInt?, val column: UInt?) : QueryError() {
        override val message: String =
            if (row == null || column == null) "Unexpected EOF"
            else "Invalid syntax at row $row, column $column"
    }

    /** A capture name error. */
    class Capture(val row: UInt, val column: UInt, val capture: CharSequence) : QueryError() {
        override val message: String = "Invalid capture name at row $row, column $column: $capture"
    }

    /** A field name error. */
    class Field(val row: UInt, val column: UInt, val field: CharSequence) : QueryError() {
        override val message: String = "Invalid field name at row $row, column $column: $field"
    }

    /** A node type error. */
    class NodeType(val row: UInt, val column: UInt, val type: CharSequence) : QueryError() {
        override val message: String = "Invalid node type at row $row, column $column: $type"
    }

    /** A pattern structure error. */
    class Structure(val row: UInt, val column: UInt) : QueryError() {
        override val message: String = "Impossible pattern at row $row, column $column"
    }

    /** A query predicate error. */
    class Predicate(
        val row: UInt,
        details: String,
        override val cause: Throwable? = null
    ) : QueryError() {
        override val message = "Invalid predicate in pattern at row $row: $details"
    }
}
