package io.github.treesitter.ktreesitter

/**
 * A function to retrieve a chunk of text at a given byte offset and point.
 *
 * The function should return `null` to indicate the end of the document.
 */
typealias ParseCallback = (byte: UInt, point: Point) -> CharSequence?

/**
 * A function that logs parsing results.
 *
 * The first argument is the log type and the second argument is the message.
 */
typealias LogFunction = (type: Parser.LogType, message: String) -> Unit

/**
 * A class that is used to produce a [syntax tree][Tree] from source code.
 *
 * @constructor Create a new instance with a certain [language], or `null` if empty.
 */
expect class Parser() {
    constructor(language: Language)

    /**
     * The language that the parser will use for parsing.
     *
     * Parsing cannot be performed while the language is `null`.
     */
    var language: Language?

    /**
     * The ranges of text that the parser will include when parsing.
     *
     * By default, the parser will always include entire documents.
     * Setting this property allows you to parse only a _portion_ of a
     * document but still return a syntax tree whose ranges match up with
     * the document as a whole. You can also pass multiple disjoint ranges.
     *
     * @throws [IllegalArgumentException] If the ranges overlap or are not in ascending order.
     */
    var includedRanges: List<Range>

    /**
     * The maximum duration in microseconds that parsing
     * should be allowed to take before halting.
     */
    var timeoutMicros: ULong

    /** The logger that the parser will use during parsing. */
    @get:Deprecated("The logger can't be called directly.", level = DeprecationLevel.HIDDEN)
    var logger: LogFunction?

    // TODO: add cancellationFlag

    /**
     * Parse a source code string and create a syntax tree.
     *
     * If you have already parsed an earlier version of this document and the document
     * has since been edited, pass the previous syntax tree to [oldTree] so that the
     * unchanged parts of it can be reused. This will save time and memory. For this
     * to work correctly, you must have already edited the old syntax tree using the
     * [Tree.edit] method in a way that exactly matches the source code changes.
     *
     * @throws [IllegalStateException]
     *  If the parser does not have a [language] assigned or
     *  if parsing was cancelled due to a [timeout][timeoutMicros].
     */
    @Throws(IllegalStateException::class)
    fun parse(source: String, oldTree: Tree? = null): Tree

    /**
     * Parse source code from a callback and create a syntax tree.
     *
     * If you have already parsed an earlier version of this document and the document
     * has since been edited, pass the previous syntax tree to [oldTree] so that the
     * unchanged parts of it can be reused. This will save time and memory. For this
     * to work correctly, you must have already edited the old syntax tree using the
     * [Tree.edit] method in a way that exactly matches the source code changes.
     *
     * @throws [IllegalStateException]
     *  If the parser does not have a [language] assigned or
     *  if parsing was cancelled due to a [timeout][timeoutMicros].
     */
    @Throws(IllegalStateException::class)
    fun parse(oldTree: Tree? = null, callback: ParseCallback): Tree

    /**
     * Instruct the parser to start the next [parse] from the beginning.
     *
     * If the parser previously failed because of a [timeout][timeoutMicros],
     * then by default, it will resume where it left off. If you don't
     * want to resume, and instead intend to use this parser to parse
     * some other document, you must call this method first.
     */
    fun reset()

    /** The type of a log message. */
    enum class LogType { LEX, PARSE }
}
