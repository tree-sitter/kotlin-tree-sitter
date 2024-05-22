package io.github.treesitter.ktreesitter

data class QueryCapture internal constructor(
    val node: Node,
    val name: String,
    val quantifier: CaptureQuantifier
) {
    override fun toString() = "QueryCapture(name=$name, node=$node, quantifier=$quantifier)"
}
