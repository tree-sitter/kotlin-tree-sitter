package io.github.treesitter.ktreesitter

expect class LookaheadIterator : Iterator<UShort> {
    val language: Language

    val currentSymbol: UShort

    val currentSymbolName: String

    fun reset(state: UShort, language: Language? = null): Boolean

    fun names(): Iterator<String>
}
