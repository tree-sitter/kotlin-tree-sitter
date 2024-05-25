package io.github.treesitter.ktreesitter

/**
 * A [quantifier](https://tree-sitter.github.io/tree-sitter/using-parsers#quantification-operators)
 * for captures.
 */
enum class CaptureQuantifier(private val quantifier: String) {
    ONE("1"),
    ONE_OR_MORE("+"),
    ZERO_OR_ONE("?"),
    ZERO_OR_MORE("*");

    override fun toString() = quantifier
}
