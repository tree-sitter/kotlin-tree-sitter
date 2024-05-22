package io.github.treesitter.ktreesitter

class QueryMatch internal constructor(
    val patternIndex: UInt,
    val captures: List<QueryCapture>
    // val settings: Map<String, String?>,
    // val assertions: Map<String, Pair<String?, Boolean>>
) {
    operator fun get(capture: String): List<Node> =
        captures.mapNotNull { if (it.name == capture) it.node else null }

    override fun toString() = "QueryMatch(patternIndex=$patternIndex, captures=$captures)"

/*
    override fun toString() = buildString {
        append("QueryMatch(patternIndex=")
        append(patternIndex)
        append(", captures=")
        append(captures)
        append(", settings=")
        append(settings)
        append(", assertions=")
        assertions.entries.joinTo(this, ", ", "{", "}") {
            val prefix = if (it.value.second) "!" else ""
            "${it.key}=$prefix${it.value.first}"
        }
        append(")")
    }
 */
}
