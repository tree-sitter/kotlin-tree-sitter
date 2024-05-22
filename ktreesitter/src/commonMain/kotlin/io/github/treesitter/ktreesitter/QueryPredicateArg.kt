package io.github.treesitter.ktreesitter

sealed interface QueryPredicateArg {
    val value: String

    value class Capture(override val value: String) : QueryPredicateArg {
        override fun toString() = "@$value"
    }

    value class Literal(override val value: String) : QueryPredicateArg {
        override fun toString() = "\"$value\""
    }
}
