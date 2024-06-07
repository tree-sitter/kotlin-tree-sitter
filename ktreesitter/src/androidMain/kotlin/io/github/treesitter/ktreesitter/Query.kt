package io.github.treesitter.ktreesitter

import dalvik.annotation.optimization.CriticalNative
import dalvik.annotation.optimization.FastNative

/**
 * A class that represents a set of patterns which match nodes in a syntax tree.
 *
 * __NOTE__: If you're targeting Android SDK level < 33,
 * you must `use` or [close] the instance to free up resources.
 *
 * @constructor
 *  Create a new query from a particular language and a
 *  string containing one or more S-expression patterns.
 * @throws [QueryError] If any error occurred while creating the query.
 */
actual class Query @Throws(QueryError::class) actual constructor(
    private val language: Language,
    private val source: String
) : AutoCloseable {
    private val self: Long = init(language.self, source)

    private val cursor: Long = cursor()

    private val captureNames: MutableList<String>

    private val predicates: List<MutableList<QueryPredicate>>

    private val settingList: List<MutableMap<String, String?>>

    private val assertionList: List<MutableMap<String, Pair<String?, Boolean>>>

    /** The number of patterns in the query. */
    @get:JvmName("getPatternCount")
    actual val patternCount: UInt
        @FastNative external get

    /** The number of captures in the query. */
    @get:JvmName("getCaptureCount")
    actual val captureCount: UInt
        @FastNative external get

    init {
        RefCleaner(this, CleanAction(self, cursor))

        predicates = List(patternCount.toInt()) { mutableListOf() }
        settingList = List(patternCount.toInt()) { mutableMapOf() }
        assertionList = List(patternCount.toInt()) { mutableMapOf() }
        captureNames = MutableList(captureCount.toInt()) {
            checkNotNull(captureNameForId(it)) {
                "Failed to get capture name at index $it"
            }
        }
        val stringValues = List(stringCount()) {
            checkNotNull(stringValueForId(it)) {
                "Failed to get string value at index $it"
            }
        }

        for (i in 0U..<patternCount) {
            val tokens = predicatesForPattern(i.toInt()) ?: continue
            val offset = startByteForPattern(i)
            val row = source.asSequence().withIndex()
                .takeWhile { it.index.toUInt() <= offset }
                .count { it.value == '\n' }.toUInt()
            var j = 0
            while (j < tokens.size) {
                var nargs = 0
                while (tokens[nargs].type != TSQueryPredicateStepTypeDone) ++nargs
                val t0 = tokens[j]
                if (t0.type == TSQueryPredicateStepTypeCapture) {
                    throw QueryError.Predicate(row, "@${captureNames[t0.value]}")
                }

                when (val pred = stringValues[t0.value]) {
                    "eq?", "not-eq?", "any-eq?", "any-not-eq?" -> {
                        if (nargs != 3) {
                            throw QueryError.Predicate(
                                row,
                                "#$pred expects 2 arguments, got ${nargs - 1}"
                            )
                        }
                        val t1 = tokens[j + 1]
                        if (t1.type != TSQueryPredicateStepTypeCapture) {
                            val value = stringValues[t1.value]
                            throw QueryError.Predicate(
                                row,
                                "first argument to #$pred must be a capture name, got \"$value\""
                            )
                        }
                        val t2 = tokens[j + 2]
                        val isPositive = pred == "eq?" || pred == "any-eq?"
                        val isAny = pred == "any-eq?" || pred == "any-not-eq?"
                        val value = if (t2.type == TSQueryPredicateStepTypeCapture) {
                            QueryPredicate.EqCapture(
                                pred,
                                captureNames[t1.value],
                                captureNames[t2.value],
                                isPositive,
                                isAny
                            )
                        } else {
                            QueryPredicate.EqString(
                                pred,
                                captureNames[t1.value],
                                stringValues[t2.value],
                                isPositive,
                                isAny
                            )
                        }
                        predicates[i] += value
                    }

                    "match?", "not-match?", "any-match?", "any-not-match?" -> {
                        if (nargs != 3) {
                            throw QueryError.Predicate(
                                row,
                                "#$pred expects 2 arguments, got ${nargs - 1}"
                            )
                        }
                        val t1 = tokens[j + 1]
                        if (t1.type != TSQueryPredicateStepTypeCapture) {
                            val value = stringValues[t1.value]
                            throw QueryError.Predicate(
                                row,
                                "first argument to #$pred must be a capture name, got \"$value\""
                            )
                        }
                        val t2 = tokens[j + 2]
                        if (t2.type != TSQueryPredicateStepTypeString) {
                            val value = captureNames[t1.value]
                            throw QueryError.Predicate(
                                row,
                                "second argument to #$pred must be a string literal, got @$value"
                            )
                        }
                        val pattern = try {
                            Regex(stringValues[t2.value])
                        } catch (cause: IllegalArgumentException) {
                            throw QueryError.Predicate(row, "pattern error", cause)
                        }
                        val value = QueryPredicate.Match(
                            pred,
                            captureNames[t1.value],
                            pattern,
                            pred == "match?" || pred == "any-match?",
                            pred == "any-match?" || pred == "any-not-match?"
                        )
                        predicates[i] += value
                    }

                    "any-of?", "not-any-of?" -> {
                        if (nargs < 3) {
                            throw QueryError.Predicate(
                                row,
                                "#$pred expects at least 2 arguments, got ${nargs - 1}"
                            )
                        }
                        val t1 = tokens[j + 1]
                        if (t1.type != TSQueryPredicateStepTypeCapture) {
                            val value = stringValues[t1.value]
                            throw QueryError.Predicate(
                                row,
                                "first argument to #$pred must be a capture name, got \"$value\""
                            )
                        }
                        val values = (2..<nargs).map {
                            val t = tokens[it]
                            if (t.type != TSQueryPredicateStepTypeString) {
                                val value = captureNames[t.value]
                                throw QueryError.Predicate(
                                    row,
                                    "arguments to #any-of? must be string literals, got @$value"
                                )
                            }
                            stringValues[t.value]
                        }
                        val value = QueryPredicate.AnyOf(
                            pred,
                            captureNames[t1.value],
                            values,
                            pred == "any-of?"
                        )
                        predicates[i] += value
                    }

                    "is?", "is-not?" -> {
                        if (nargs == 1 || nargs > 3) {
                            throw QueryError.Predicate(
                                row,
                                "#$pred expects 1-2 arguments, got ${nargs - 1}"
                            )
                        }
                        val t1 = tokens[j + 1]
                        if (t1.type != TSQueryPredicateStepTypeString) {
                            val value = captureNames[t1.value]
                            throw QueryError.Predicate(
                                row,
                                "first argument to #$pred must be a string literal, got @$value"
                            )
                        }
                        val key = stringValues[t1.value]
                        val value = if (nargs == 2) {
                            Pair(null, pred == "is?")
                        } else {
                            val t2 = tokens[j + 2]
                            if (t2.type != TSQueryPredicateStepTypeString) {
                                val value = captureNames[t2.value]
                                throw QueryError.Predicate(
                                    row,
                                    "second argument to #$pred must be a string literal, " +
                                        "got @$value"
                                )
                            }
                            Pair(stringValues[t2.value], pred == "is?")
                        }
                        assertionList[i][key] = value
                    }

                    "set!" -> {
                        if (nargs == 1 || nargs > 3) {
                            throw QueryError.Predicate(
                                row,
                                "#$pred expects 1-2 arguments, got ${nargs - 1}"
                            )
                        }
                        val t1 = tokens[j + 1]
                        if (t1.type != TSQueryPredicateStepTypeString) {
                            val value = captureNames[t1.value]
                            throw QueryError.Predicate(
                                row,
                                "first argument to #$pred must be a string literal, got @$value"
                            )
                        }
                        val key = stringValues[t1.value]
                        val value = if (nargs == 2) {
                            null
                        } else {
                            val t2 = tokens[j + 2]
                            if (t2.type != TSQueryPredicateStepTypeString) {
                                val value = captureNames[t2.value]
                                throw QueryError.Predicate(
                                    row,
                                    "second argument to #$pred must be a string literal, got @$value"
                                )
                            }
                            stringValues[t2.value]
                        }
                        settingList[i][key] = value
                    }

                    else -> {
                        val args = (1..<nargs).map {
                            val t = tokens[it]
                            if (t.type == TSQueryPredicateStepTypeString) {
                                QueryPredicateArg.Literal(stringValues[t.value])
                            } else {
                                QueryPredicateArg.Capture(captureNames[t.value])
                            }
                        }
                        predicates[i] += QueryPredicate.Generic(pred, args)
                    }
                }

                j += nargs + 1
            }
        }
    }

    /**
     * The maximum number of in-progress matches.
     *
     * @throws [IllegalArgumentException] If the match limit is set to `0`.
     */
    @get:JvmName("getMatchLimit")
    @set:JvmName("setMatchLimit")
    actual var matchLimit: UInt
        @FastNative external get

        @FastNative external set

    /**
     * The maximum start depth for the query.
     *
     * This prevents cursors from exploring children nodes at a certain depth.
     * Note that if a pattern includes many children, then they will still be checked.
     *
     * Default: `UInt.MAX_VALUE`
     */
    @get:JvmName("getMaxStartDepth")
    @set:JvmName("setMaxStartDepth")
    actual var maxStartDepth: UInt = UInt.MAX_VALUE
        @FastNative external set

    /**
     * The range of bytes in which the query will be executed.
     *
     * Default: `UInt.MIN_VALUE..UInt.MAX_VALUE`
     */
    actual var byteRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE
        set(value) {
            nativeSetByteRange(value.first.toInt(), value.last.toInt())
            field = value
        }

    /**
     * The range of points in which the query will be executed.
     *
     * Default: `Point.MIN..Point.MAX`
     */
    actual var pointRange: ClosedRange<Point> = Point.MIN..Point.MAX
        set(value) {
            nativeSetPointRange(value.start, value.endInclusive)
            field = value
        }

    /**
     * Check if the query exceeded its maximum number of
     * in-progress matches during its last execution.
     */
    @get:JvmName("didExceedMatchLimit")
    actual val didExceedMatchLimit: Boolean
        @FastNative external get

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
    @JvmOverloads
    actual fun matches(
        node: Node,
        predicate: QueryPredicate.(QueryMatch) -> Boolean
    ): Sequence<QueryMatch> {
        exec(node)
        return sequence {
            var match = nextMatch(node.tree)
            while (match != null) {
                val result = match.check(node.tree, predicate)
                if (result != null) yield(result)
                match = nextMatch(node.tree)
            }
        }
    }

    /**
     * Iterate over all the individual captures in the order that they appear.
     *
     * This is useful if you don't care about _which_ pattern matched.
     *
     * @param node The node that the query will run on.
     * @param predicate A function that handles custom predicates.
     */
    @JvmOverloads
    actual fun captures(
        node: Node,
        predicate: QueryPredicate.(QueryMatch) -> Boolean
    ): Sequence<Pair<UInt, QueryMatch>> {
        exec(node)
        return sequence {
            var capture = nextCapture(node.tree)
            while (capture != null) {
                val index = capture.first
                val match = capture.second.check(node.tree, predicate)
                if (match != null) yield(index to match)
                capture = nextCapture(node.tree)
            }
        }
    }

    /**
     * Get the property settings for the given pattern index.
     *
     * Properties are set using the `#set!` predicate.
     *
     * @return A map of properties with optional values.
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @JvmName("settings")
    @Throws(IndexOutOfBoundsException::class)
    actual fun settings(index: UInt): Map<String, String?> {
        if (index >= patternCount)
            throw IndexOutOfBoundsException("Pattern index $index is out of bounds")
        return settingList[index]
    }

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
    @JvmName("assertions")
    @Throws(IndexOutOfBoundsException::class)
    actual fun assertions(index: UInt): Map<String, Pair<String?, Boolean>> {
        if (index >= patternCount)
            throw IndexOutOfBoundsException("Pattern index $index is out of bounds")
        return assertionList[index]
    }

    /**
     * Disable a certain pattern within a query.
     *
     * This prevents the pattern from matching and removes most of the overhead
     * associated with the pattern. Currently, there is no way to undo this.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @FastNative
    @JvmName("disablePattern")
    @Throws(IndexOutOfBoundsException::class)
    actual external fun disablePattern(index: UInt)

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
    actual fun disableCapture(name: String) {
        if (!captureNames.remove(name))
            throw NoSuchElementException("Capture @$name does not exist")
        nativeDisableCapture(name)
    }

    /**
     * Get the byte offset where the given pattern starts in the query's source.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @FastNative
    @JvmName("startByteForPattern")
    @Throws(IndexOutOfBoundsException::class)
    actual external fun startByteForPattern(index: UInt): UInt

    /**
     * Check if the pattern with the given index has a single root node.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @FastNative
    @JvmName("isPatternRooted")
    @Throws(IndexOutOfBoundsException::class)
    actual external fun isPatternRooted(index: UInt): Boolean

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
    @FastNative
    @JvmName("isPatternNonLocal")
    @Throws(IndexOutOfBoundsException::class)
    actual external fun isPatternNonLocal(index: UInt): Boolean

    /**
     * Check if a pattern is guaranteed to match
     * once a given byte offset is reached.
     */
    @FastNative
    @JvmName("isPatternGuaranteedAtStep")
    @Throws(IndexOutOfBoundsException::class)
    actual fun isPatternGuaranteedAtStep(offset: UInt): Boolean {
        if (offset >= source.length.toUInt())
            throw IndexOutOfBoundsException("Offset $offset exceeds EOF")
        return nativeIsPatternGuaranteedAtStep(offset.toInt())
    }

    override fun toString() = "Query(language=$language, source=$source)"

    override fun close() = delete(self, cursor)

    @FastNative
    private external fun stringCount(): Int

    @FastNative
    private external fun exec(node: Node)

    private external fun nextMatch(tree: Tree): QueryMatch?

    private external fun nextCapture(tree: Tree): Pair<UInt, QueryMatch>?

    @FastNative
    private external fun captureNameForId(index: Int): String?

    @FastNative
    private external fun stringValueForId(index: Int): String?

    @FastNative
    private external fun nativeSetByteRange(start: Int, end: Int)

    @FastNative
    private external fun nativeSetPointRange(start: Point, end: Point)

    @FastNative
    private external fun nativeDisableCapture(name: String)

    @FastNative
    private external fun nativeIsPatternGuaranteedAtStep(index: Int): Boolean

    private external fun predicatesForPattern(index: Int): List<IntArray>?

    private inline fun QueryMatch.check(
        tree: Tree,
        predicate: QueryPredicate.(QueryMatch) -> Boolean
    ): QueryMatch? {
        if (tree.text() == null) return this
        val result = predicates[patternIndex].all {
            if (it !is QueryPredicate.Generic) it(this) else predicate(it, this)
        }
        return if (result) this else null
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline operator fun <T> List<T>.get(index: UInt) = get(index.toInt())

    private inline val IntArray.value: Int
        inline get() = get(0)

    private inline val IntArray.type: Int
        inline get() = get(1)

    private class CleanAction(private val query: Long, private val cursor: Long) : Runnable {
        override fun run() = delete(query, cursor)
    }

    @Suppress("ConstPropertyName")
    private companion object {
        private const val TSQueryPredicateStepTypeDone = 0

        private const val TSQueryPredicateStepTypeCapture = 1

        private const val TSQueryPredicateStepTypeString = 2

        @JvmStatic
        @Throws(QueryError::class)
        private external fun init(language: Long, query: String): Long

        @JvmStatic
        @CriticalNative
        private external fun cursor(): Long

        @JvmStatic
        @CriticalNative
        private external fun delete(query: Long, cursor: Long)
    }
}
