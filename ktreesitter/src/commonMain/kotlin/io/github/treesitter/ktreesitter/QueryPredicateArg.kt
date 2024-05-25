package io.github.treesitter.ktreesitter

/**
 * An argument to a [QueryPredicate].
 *
 * @property value The value of the argument.
 */
sealed interface QueryPredicateArg {
    val value: String

    /** A capture argument (`@value`). */
    value class Capture(override val value: String) : QueryPredicateArg {
        override fun toString() = "@$value"
    }

    /** A literal string argument (`"value"`). */
    value class Literal(override val value: String) : QueryPredicateArg {
        override fun toString() = "\"$value\""
    }
}
