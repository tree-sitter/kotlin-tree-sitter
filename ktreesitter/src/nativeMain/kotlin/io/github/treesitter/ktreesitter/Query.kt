package io.github.treesitter.ktreesitter

import cnames.structs.TSQuery
import cnames.structs.TSQueryCursor
import io.github.treesitter.ktreesitter.internal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.*

/**
 * A class that represents a set of patterns which match nodes in a syntax tree.
 *
 * @constructor
 *  Create a new query from a particular language and a
 *  string containing one or more S-expression patterns.
 * @throws [QueryError] If any error occurred while creating the query.
 */
@OptIn(ExperimentalForeignApi::class)
actual class Query @Throws(QueryError::class) actual constructor(
    language: Language,
    source: String
) {
    private val self: CPointer<TSQuery>

    private val cursor: CPointer<TSQueryCursor>

    private val sourceLength = source.length.toUInt()

    private val captureNames: MutableList<String>

    private val predicates: MutableMap<UInt, MutableList<QueryPredicate>>

    private val settings: MutableMap<UInt, MutableMap<String, String?>>

    private val assertions: MutableMap<UInt, MutableMap<String, Pair<String?, Boolean>>>

    /** The number of patterns in the query. */
    actual val patternCount: UInt

    /** The number of captures in the query. */
    actual val captureCount: UInt

    init {
        val arena = Arena()
        val errorOffset = arena.alloc<UIntVar>()
        val errorType = arena.alloc<TSQueryError.Var>()
        val query = ts_query_new(
            language.self,
            source,
            source.length.convert(),
            errorOffset.ptr,
            errorType.ptr
        )

        if (query == null) {
            var start = 0U
            var row = 1U
            val offset = errorOffset.value
            for (line in source.splitToSequence('\n')) {
                val lineEnd = start + line.length.toUInt() + 1U
                if (lineEnd > offset) break
                start = lineEnd
                row += 1U
            }
            val column = offset - start + 1U

            val exception = when (errorType.value) {
                TSQueryError.TSQueryErrorSyntax -> {
                    if (offset < source.length.toUInt()) {
                        QueryError.Syntax(row, column)
                    } else {
                        QueryError.Syntax(null, null)
                    }
                }
                TSQueryError.TSQueryErrorCapture -> {
                    val suffix = source.subSequence(offset.toInt(), source.length)
                    val end = suffix.indexOfFirst { !kts_is_valid_predicate_char(it.code) }
                    val error = suffix.subSequence(0, end.takeIf { it > -1 } ?: suffix.length)
                    QueryError.Capture(row, column, error)
                }
                TSQueryError.TSQueryErrorNodeType -> {
                    val suffix = source.subSequence(offset.toInt(), source.length)
                    val end = suffix.indexOfFirst { !kts_is_valid_identifier_char(it.code) }
                    val error = suffix.subSequence(0, end.takeIf { it > -1 } ?: suffix.length)
                    QueryError.NodeType(row, column, error)
                }
                TSQueryError.TSQueryErrorField -> {
                    val suffix = source.subSequence(offset.toInt(), source.length)
                    val end = suffix.indexOfFirst { !kts_is_valid_identifier_char(it.code) }
                    val error = suffix.subSequence(0, end.takeIf { it > -1 } ?: suffix.length)
                    QueryError.Field(row, column, error)
                }
                TSQueryError.TSQueryErrorStructure -> QueryError.Structure(row, column)
                // language errors are handled in the Language class
                else -> IllegalStateException("Unexpected query error")
            }
            arena.clear()
            throw exception
        }
        arena.clear()

        self = query
        cursor = ts_query_cursor_new()!!
        patternCount = ts_query_pattern_count(self)
        captureCount = ts_query_capture_count(self)
        predicates = mutableMapOf()
        settings = mutableMapOf()
        assertions = mutableMapOf()
        captureNames = MutableList(captureCount.toInt()) {
            memScoped {
                val length = alloc<UIntVar>()
                val name = ts_query_capture_name_for_id(self, it.convert(), length.ptr)
                if (name == null || length.value == 0U)
                    error("Failed to get capture name at index $it")
                name.toKString()
            }
        }
        val stringValues = List(ts_query_string_count(self).convert()) {
            memScoped {
                val length = alloc<UIntVar>()
                val value = ts_query_string_value_for_id(self, it.convert(), length.ptr)
                if (value == null || length.value == 0U)
                    error("Failed to get string value at index $it")
                value.toKString()
            }
        }

        for (i in 0U..<patternCount) {
            var steps = 0U
            var tokens = memScoped {
                val count = alloc<UIntVar>()
                val result = ts_query_predicates_for_pattern(self, i, count.ptr)
                steps = count.value
                if (steps > 0U) result else null
            } ?: break
            val offset = ts_query_start_byte_for_pattern(self, i)
            val row = source.asSequence().withIndex()
                .takeWhile { it.index.toUInt() <= offset }
                .count { it.value == '\n' }.toUInt() + 1U
            var j = 0U
            while (j < steps) {
                var nargs = 0L
                while (tokens[nargs].type != TSQueryPredicateStepTypeDone) ++nargs
                val t0 = tokens[0]
                if (t0.type == TSQueryPredicateStepTypeCapture) {
                    throw QueryError.Predicate(row, "@${captureNames[t0.value_id]}")
                }

                when (val pred = stringValues[t0.value_id]) {
                    "eq?", "not-eq?", "any-eq?", "any-not-eq?" -> {
                        if (nargs != 3L) {
                            throw QueryError.Predicate(
                                row,
                                "#$pred expects 2 arguments, got ${nargs - 1L}"
                            )
                        }
                        val t1 = tokens[1]
                        if (t1.type != TSQueryPredicateStepTypeCapture) {
                            val value = stringValues[t1.value_id]
                            throw QueryError.Predicate(
                                row,
                                "first argument to #$pred must be a capture name, got \"$value\""
                            )
                        }
                        val t2 = tokens[2]
                        val isPositive = pred == "eq?" || pred == "any-eq?"
                        val isAny = pred == "any-eq?" || pred == "any-not-eq?"
                        val value = if (t2.type == TSQueryPredicateStepTypeCapture) {
                            QueryPredicate.EqCapture(
                                pred,
                                captureNames[t1.value_id],
                                captureNames[t2.value_id],
                                isPositive,
                                isAny
                            )
                        } else {
                            QueryPredicate.EqString(
                                pred,
                                captureNames[t1.value_id],
                                stringValues[t2.value_id],
                                isPositive,
                                isAny
                            )
                        }
                        predicates.put(i, value)
                    }

                    "match?", "not-match?", "any-match?", "any-not-match?" -> {
                        if (nargs != 3L) {
                            throw QueryError.Predicate(
                                row,
                                "#$pred expects 2 arguments, got ${nargs - 1L}"
                            )
                        }
                        val t1 = tokens[1]
                        if (t1.type != TSQueryPredicateStepTypeCapture) {
                            val value = stringValues[t1.value_id]
                            throw QueryError.Predicate(
                                row,
                                "first argument to #$pred must be a capture name, got \"$value\""
                            )
                        }
                        val t2 = tokens[2]
                        if (t2.type != TSQueryPredicateStepTypeString) {
                            val value = captureNames[t1.value_id]
                            throw QueryError.Predicate(
                                row,
                                "second argument to #$pred must be a string literal, got @$value"
                            )
                        }
                        val pattern = try {
                            Regex(stringValues[t2.value_id])
                        } catch (cause: IllegalArgumentException) {
                            throw QueryError.Predicate(row, "pattern error", cause)
                        }
                        val value = QueryPredicate.Match(
                            pred,
                            captureNames[t1.value_id],
                            pattern,
                            pred == "match?" || pred == "any-match?",
                            pred == "any-match?" || pred == "any-not-match?"
                        )
                        predicates.put(i, value)
                    }

                    "any-of?", "not-any-of?" -> {
                        if (nargs < 3L) {
                            throw QueryError.Predicate(
                                row,
                                "#$pred expects at least 2 arguments, got ${nargs - 1L}"
                            )
                        }
                        val t1 = tokens[1]
                        if (t1.type != TSQueryPredicateStepTypeCapture) {
                            val value = stringValues[t1.value_id]
                            throw QueryError.Predicate(
                                row,
                                "first argument to #$pred must be a capture name, got \"$value\""
                            )
                        }
                        val values = (2L..<nargs).map {
                            val t = tokens[it]
                            if (t.type != TSQueryPredicateStepTypeString) {
                                val value = captureNames[t.value_id]
                                throw QueryError.Predicate(
                                    row,
                                    "arguments to #any-of? must be string literals, got @$value"
                                )
                            }
                            stringValues[t.value_id]
                        }
                        val value = QueryPredicate.AnyOf(
                            pred,
                            captureNames[t1.value_id],
                            values,
                            pred == "any-of?"
                        )
                        predicates.put(i, value)
                    }

                    "is?", "is-not?" -> {
                        if (nargs == 1L || nargs > 3L) {
                            throw QueryError.Predicate(
                                row,
                                "#$pred expects 1-2 arguments, got ${nargs - 1L}"
                            )
                        }
                        val t1 = tokens[1]
                        if (t1.type != TSQueryPredicateStepTypeString) {
                            val value = captureNames[t1.value_id]
                            throw QueryError.Predicate(
                                row,
                                "first argument to #$pred must be a string literal, got @$value"
                            )
                        }
                        val key = stringValues[t1.value_id]
                        val value = if (nargs == 2L) {
                            Pair(null, pred == "is?")
                        } else {
                            val t2 = tokens[2]
                            if (t2.type != TSQueryPredicateStepTypeString) {
                                val value = captureNames[t2.value_id]
                                throw QueryError.Predicate(
                                    row,
                                    "second argument to #$pred must be a string literal, got @$value"
                                )
                            }
                            Pair(stringValues[t2.value_id], pred == "is?")
                        }
                        assertions.put(i, key, value)
                    }

                    "set!" -> {
                        if (nargs == 1L || nargs > 3L) {
                            throw QueryError.Predicate(
                                row,
                                "#$pred expects 1-2 arguments, got ${nargs - 1L}"
                            )
                        }
                        val t1 = tokens[1]
                        if (t1.type != TSQueryPredicateStepTypeString) {
                            val value = captureNames[t1.value_id]
                            throw QueryError.Predicate(
                                row,
                                "first argument to #$pred must be a string literal, got @$value"
                            )
                        }
                        val key = stringValues[t1.value_id]
                        val value = if (nargs == 2L) {
                            null
                        } else {
                            val t2 = tokens[2]
                            if (t2.type != TSQueryPredicateStepTypeString) {
                                val value = captureNames[t2.value_id]
                                throw QueryError.Predicate(
                                    row,
                                    "second argument to #$pred must be a string literal, got @$value"
                                )
                            }
                            stringValues[t2.value_id]
                        }
                        settings.put(i, key, value)
                    }

                    else -> {
                        val args = (1L..<nargs).map {
                            val t = tokens[it]
                            if (t.type == TSQueryPredicateStepTypeString) {
                                QueryPredicateArg.Literal(stringValues[t.value_id])
                            } else {
                                QueryPredicateArg.Capture(captureNames[t.value_id])
                            }
                        }
                        predicates.put(i, QueryPredicate.Generic(pred, args))
                    }
                }

                j += nargs.toUInt() + 1U
                tokens = interpretCPointer(tokens.rawValue + nargs)!!
            }
        }
    }

    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val queryCleaner = createCleaner(self, ::ts_query_delete)

    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val cursorCleaner = createCleaner(cursor, ::ts_query_cursor_delete)

    /**
     * The maximum number of in-progress matches.
     *
     * @throws [IllegalArgumentException] If the match limit is set to `0`.
     */
    actual var matchLimit: UInt
        get() = ts_query_cursor_match_limit(cursor)
        set(value) {
            require(value > 0U) { "The match limit cannot be 0" }
            ts_query_cursor_set_match_limit(cursor, value)
        }

    /**
     * The maximum start depth for the query.
     *
     * This prevents cursors from exploring children nodes at a certain depth.
     * Note that if a pattern includes many children, then they will still be checked.
     *
     * Default: `UInt.MAX_VALUE`
     */
    actual var maxStartDepth: UInt = UInt.MAX_VALUE
        set(value) {
            ts_query_cursor_set_max_start_depth(cursor, value)
            field = value
        }

    /**
     * The range of bytes in which the query will be executed.
     *
     * Default: `UInt.MIN_VALUE..UInt.MAX_VALUE`
     */
    actual var byteRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE
        set(value) {
            ts_query_cursor_set_byte_range(cursor, value.first, value.last)
            field = value
        }

    /**
     * The range of points in which the query will be executed.
     *
     * Default: `Point.MIN..Point.MAX`
     */
    actual var pointRange: ClosedRange<Point> = Point.MIN..Point.MAX
        set(value) {
            val start = cValue<TSPoint> { from(value.start) }
            val end = cValue<TSPoint> { from(value.endInclusive) }
            ts_query_cursor_set_point_range(cursor, start, end)
            field = value
        }

    /**
     * Check if the query exceeded its maximum number of
     * in-progress matches during its last execution.
     */
    actual val didExceedMatchLimit: Boolean
        get() = ts_query_cursor_did_exceed_match_limit(cursor)

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
    @Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
    actual fun matches(
        node: Node,
        predicate: QueryPredicate.(QueryMatch) -> Boolean = { true }
    ): Sequence<QueryMatch> {
        ts_query_cursor_exec(cursor, self, node.self)
        return sequence {
            memScoped {
                val match = alloc<TSQueryMatch>()
                while (ts_query_cursor_next_match(cursor, match.ptr)) {
                    match.convert(node.tree, predicate)?.let { yield(it) }
                }
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
    @Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
    actual fun captures(
        node: Node,
        predicate: QueryPredicate.(QueryMatch) -> Boolean = { true }
    ): Sequence<Pair<UInt, QueryMatch>> {
        ts_query_cursor_exec(cursor, self, node.self)
        return sequence {
            memScoped {
                val match = alloc<TSQueryMatch>()
                val index = alloc<UIntVar>()
                while (ts_query_cursor_next_capture(cursor, match.ptr, index.ptr)) {
                    match.convert(node.tree, predicate)?.let { yield(index.value to it) }
                }
            }
        }
    }

    /**
     * Get the property settings for the given pattern index.
     *
     * Properties are set using the `#set!` predicate.
     *
     * @return A map of properties with optional values.
     */
    actual fun settings(index: UInt): Map<String, String?> {
        if (index >= patternCount)
            throw IndexOutOfBoundsException("Index $index exceeds count $patternCount")
        return settings[index] ?: emptyMap()
    }

    /**
     * Get the property assertions for the given pattern index.
     *
     * Assertions are performed using the `#is?` and `#is-not?` predicates.
     *
     * @return
     *  A map of assertions, where the first item is the optional property value
     *  and the second item indicates whether the assertion was positive or negative.
     */
    actual fun assertions(index: UInt): Map<String, Pair<String?, Boolean>> {
        if (index >= patternCount)
            throw IndexOutOfBoundsException("Index $index exceeds count $patternCount")
        return assertions[index] ?: emptyMap()
    }

    /**
     * Disable a certain pattern within a query.
     *
     * This prevents the pattern from matching and removes most of the overhead
     * associated with the pattern. Currently, there is no way to undo this.
     */
    actual fun disablePattern(index: UInt) {
        if (index >= patternCount)
            throw IndexOutOfBoundsException("Index $index exceeds count $patternCount")
        ts_query_disable_pattern(self, index)
    }

    /**
     * Disable a certain capture within a query.
     *
     * This prevents the capture from being returned in matches,
     * and also avoids most resource usage associated with recording
     * the capture. Currently, there is no way to undo this.
     */
    actual fun disableCapture(name: String) {
        if (!captureNames.remove(name))
            throw NoSuchElementException("Capture @$name does not exist")
        ts_query_disable_capture(self, name, name.length.convert())
    }

    /** Get the byte offset where the given pattern starts in the query's source. */
    actual fun startByteForPattern(index: UInt): UInt {
        if (index >= patternCount)
            throw IndexOutOfBoundsException("Index $index exceeds count $patternCount")
        return ts_query_start_byte_for_pattern(self, index)
    }

    /** Check if the pattern with the given index has a single root node. */
    actual fun isPatternRooted(index: UInt): Boolean {
        if (index >= patternCount)
            throw IndexOutOfBoundsException("Index $index exceeds count $patternCount")
        return ts_query_is_pattern_rooted(self, index)
    }

    /**
     * Check if the pattern with the given index is "non-local".
     *
     * A non-local pattern has multiple root nodes and can match within a
     * repeating sequence of nodes, as specified by the grammar. Non-local
     * patterns disable certain optimizations that would otherwise be possible
     * when executing a query on a specific range of a syntax tree.
     */
    actual fun isPatternNonLocal(index: UInt): Boolean {
        if (index >= patternCount)
            throw IndexOutOfBoundsException("Index $index exceeds count $patternCount")
        return ts_query_is_pattern_non_local(self, index)
    }

    /**
     * Check if a pattern is guaranteed to match
     * once a given byte offset is reached.
     */
    actual fun isPatternGuaranteedAtStep(offset: UInt): Boolean {
        if (offset >= sourceLength)
            throw IndexOutOfBoundsException("Offset $offset exceeds EOF")
        return ts_query_is_pattern_guaranteed_at_step(self, offset)
    }

    private fun TSQueryMatch.convert(
        tree: Tree,
        predicate: QueryPredicate.(QueryMatch) -> Boolean
    ): QueryMatch? {
        val index = pattern_index.convert<UInt>()
        val captures = (UShort.MIN_VALUE..<capture_count).map {
            val c = captures!![it.convert<Long>()]
            val quantifier = ts_query_capture_quantifier_for_id(self, index, c.index)
            QueryCapture(
                Node(c.node.readValue(), tree),
                this@Query.captureNames[c.index],
                when (quantifier) {
                    TSQuantifier.TSQuantifierOne -> CaptureQuantifier.ONE
                    TSQuantifier.TSQuantifierOneOrMore -> CaptureQuantifier.ONE_OR_MORE
                    TSQuantifier.TSQuantifierZeroOrOne -> CaptureQuantifier.ZERO_OR_ONE
                    TSQuantifier.TSQuantifierZeroOrMore -> CaptureQuantifier.ZERO_OR_MORE
                    else -> error("Invalid capture quantifier at pattern index $index")
                }
            )
        }
        return QueryMatch(index, captures).takeIf { match ->
            tree.text() == null || predicates[index]?.all {
                if (it !is QueryPredicate.Generic) it(match) else predicate(it, match)
            } ?: true
        }
    }

    private inline fun <T> MutableMap<UInt, MutableList<T>>.put(i: UInt, value: T) {
        getOrPut(i) { mutableListOf() }.add(value)
    }

    private inline fun <K, V> MutableMap<UInt, MutableMap<K, V>>.put(i: UInt, key: K, value: V) {
        getOrPut(i) { mutableMapOf() }[key] = value
    }

    private inline operator fun <T> List<T>.get(index: UInt) = get(index.toInt())
}
