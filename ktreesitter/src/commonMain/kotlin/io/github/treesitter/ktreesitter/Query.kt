package io.github.treesitter.ktreesitter

/**
 * A class that represents a set of patterns which match nodes in a syntax tree.
 *
 * @constructor
 *  Create a new query from a particular language and a
 *  string containing one or more S-expression patterns.
 * @throws [QueryError] If any error occurred while creating the query.
 */
expect class Query @Throws(QueryError::class) constructor(language: Language, source: String) {
    /** The number of patterns in the query. */
    val patternCount: UInt

    /** The number of captures in the query. */
    val captureCount: UInt

    /**
     * The maximum duration in microseconds that query
     * execution should be allowed to take before halting.
     *
     * Default: `0`
     *
     * @since 0.23.0
     */
    var timeoutMicros: ULong

    /**
     * The maximum number of in-progress matches.
     *
     * Default: `UInt.MAX_VALUE`
     *
     * @throws [IllegalArgumentException] If the match limit is set to `0`.
     */
    var matchLimit: UInt

    /**
     * The maximum start depth for the query.
     *
     * This prevents cursors from exploring children nodes at a certain depth.
     * Note that if a pattern includes many children, then they will still be checked.
     *
     * Default: `UInt.MAX_VALUE`
     */
    var maxStartDepth: UInt

    /**
     * The range of bytes in which the query will be executed.
     *
     * Default: `UInt.MIN_VALUE..UInt.MAX_VALUE`
     */
    var byteRange: UIntRange

    /**
     * The range of points in which the query will be executed.
     *
     * Default: `Point.MIN..Point.MAX`
     */
    var pointRange: ClosedRange<Point>

    /**
     * Check if the query exceeded its maximum number of
     * in-progress matches during its last execution.
     */
    val didExceedMatchLimit: Boolean

    /**
     * Iterate over all the matches in the order that they were found.
     *
     * #### Example
     *
     * ```kotlin
     * query.matches(tree.rootNode) {
     *      if (name != "ieq?") return@matches true
     *      val node = it[(args[0] as QueryPredicateArg.Capture).value].first()
     *      val value = (args[1] as QueryPredicateArg.Literal).value
     *      value.equals(node.text()?.toString(), ignoreCase = true)
     *  }
     * ```
     *
     * @param node The node that the query will run on.
     * @param predicate A function that handles custom predicates.
     */
    fun matches(
        node: Node,
        predicate: QueryPredicate.(QueryMatch) -> Boolean = { true }
    ): Sequence<QueryMatch>

    /**
     * Iterate over all the individual captures in the order that they appear.
     *
     * This is useful if you don't care about _which_ pattern matched.
     *
     * @param node The node that the query will run on.
     * @param predicate A function that handles custom predicates.
     */
    fun captures(
        node: Node,
        predicate: QueryPredicate.(QueryMatch) -> Boolean = { true }
    ): Sequence<Pair<UInt, QueryMatch>>

    /**
     * Get the property settings for the given pattern index.
     *
     * Properties are set using the `#set!` predicate.
     *
     * @return A map of properties with optional values.
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    fun settings(index: UInt): Map<String, String?>

    /**
     * Get the property assertions for the given pattern index.
     *
     * Assertions are performed using the `#is?` and `#is-not?` predicates.
     *
     * @return
     *  A map of assertions, where the first item is the optional property value
     *  and the second item indicates whether the assertion was positive or negative.
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @Throws(IndexOutOfBoundsException::class)
    fun assertions(index: UInt): Map<String, Pair<String?, Boolean>>

    /**
     * Disable a certain pattern within a query.
     *
     * This prevents the pattern from matching and removes most of the overhead
     * associated with the pattern. Currently, there is no way to undo this.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @Throws(IndexOutOfBoundsException::class)
    fun disablePattern(index: UInt)

    /**
     * Disable a certain capture within a query.
     *
     * This prevents the capture from being returned in matches,
     * and also avoids most resource usage associated with recording
     * the capture. Currently, there is no way to undo this.
     *
     * @throws [NoSuchElementException] If the capture does not exist.
     */
    @Throws(NoSuchElementException::class)
    fun disableCapture(name: String)

    /**
     * Get the byte offset where the given pattern starts in the query's source.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @Throws(IndexOutOfBoundsException::class)
    fun startByteForPattern(index: UInt): UInt

    /**
     * Get the byte offset where the given pattern ends in the query's source.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     * @since 0.23.0
     */
    @Throws(IndexOutOfBoundsException::class)
    fun endByteForPattern(index: UInt): UInt

    /**
     * Check if the pattern with the given index has a single root node.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @Throws(IndexOutOfBoundsException::class)
    fun isPatternRooted(index: UInt): Boolean

    /**
     * Check if the pattern with the given index is "non-local".
     *
     * A non-local pattern has multiple root nodes and can match within a
     * repeating sequence of nodes, as specified by the grammar. Non-local
     * patterns disable certain optimizations that would otherwise be possible
     * when executing a query on a specific range of a syntax tree.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @Throws(IndexOutOfBoundsException::class)
    fun isPatternNonLocal(index: UInt): Boolean

    /**
     * Check if a pattern is guaranteed to match once a given byte offset is reached.
     *
     * @throws [IndexOutOfBoundsException] If the offset exceeds the source length.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun isPatternGuaranteedAtStep(offset: UInt): Boolean
}
