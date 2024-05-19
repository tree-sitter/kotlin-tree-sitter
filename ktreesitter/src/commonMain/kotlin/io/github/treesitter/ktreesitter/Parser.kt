package io.github.treesitter.ktreesitter

typealias ParseCallback = (UInt, Point) -> String?
typealias LogFunction = (Parser.LogType, String) -> Unit

expect class Parser() {
    constructor(language: Language)

    var language: Language?
    var includedRanges: List<Range>
    var timeoutMicros: ULong
    var logger: LogFunction?
    // TODO: add cancellationFlag

    fun parse(source: String, oldTree: Tree? = null): Tree
    fun parse(oldTree: Tree? = null, callback: ParseCallback): Tree
    fun reset()

    enum class LogType { LEX, PARSE }
}
