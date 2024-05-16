package io.github.treesitter.ktreesitter

expect class LookaheadIterator : Iterable<LookaheadIterator.Symbol> {
    val language: Language

    val currentSymbol: UShort

    val currentSymbolName: String?

    fun reset(state: UShort, language: Language? = null): Boolean

    fun next(): Boolean

    fun symbols(): Sequence<UShort>

    fun symbolNames(): Sequence<String?>

    class Symbol(id: UShort, name: String?) {
        val id: UShort
        val name: String?
    }
}
