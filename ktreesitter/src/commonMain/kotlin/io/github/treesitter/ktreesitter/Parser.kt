package io.github.treesitter.ktreesitter

typealias ParseCallback = (UInt, Point) -> CharSequence?

expect class Parser() {
    constructor(language: Language)

    var language: Language?
    var includedRanges: List<Range>
    var timeoutMicros: ULong

    // TODO: add logger, cancellationFlag

    fun parse(source: String, oldTree: Tree? = null): Tree
    fun parse(oldTree: Tree? = null, callback: ParseCallback): Tree
    fun reset()
}
