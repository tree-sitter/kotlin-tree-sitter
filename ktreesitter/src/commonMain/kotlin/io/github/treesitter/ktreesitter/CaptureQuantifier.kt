package io.github.treesitter.ktreesitter

enum class CaptureQuantifier(private val quantifier: String) {
    ONE("1"),
    ONE_OR_MORE("+"),
    ZERO_OR_ONE("?"),
    ZERO_OR_MORE("*");

    override fun toString() = quantifier
}
