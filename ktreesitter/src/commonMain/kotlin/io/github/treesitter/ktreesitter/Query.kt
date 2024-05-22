package io.github.treesitter.ktreesitter

expect class Query(language: Language, source: String) {
    val patternCount: UInt
    val captureCount: UInt
    var matchLimit: UInt
    var maxStartDepth: UInt
    var byteRange: UIntRange
    var pointRange: ClosedRange<Point>
    val didExceedMatchLimit: Boolean

    fun matches(
        node: Node,
        predicate: QueryPredicate.(QueryMatch) -> Boolean = { true }
    ): Sequence<QueryMatch>
    fun captures(
        node: Node,
        predicate: QueryPredicate.(QueryMatch) -> Boolean = { true }
    ): Sequence<Pair<UInt, QueryMatch>>
    fun settings(index: UInt): Map<String, String?>
    fun assertions(index: UInt): Map<String, Pair<String?, Boolean>>
    fun disablePattern(index: UInt)
    fun disableCapture(name: String)
    fun startByteForPattern(index: UInt): UInt
    fun isPatternRooted(index: UInt): Boolean
    fun isPatternNonLocal(index: UInt): Boolean
    fun isPatternGuaranteedAtStep(offset: UInt): Boolean
}
