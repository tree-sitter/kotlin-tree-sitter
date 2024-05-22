package io.github.treesitter.ktreesitter

expect class Language {
    val version: UInt
    val symbolCount: UInt
    val stateCount: UInt
    val fieldCount: UInt

    fun symbolName(symbol: UShort): String?
    fun symbolForName(name: String, isNamed: Boolean): UShort?
    fun isNamed(symbol: UShort): Boolean
    fun isVisible(symbol: UShort): Boolean
    fun fieldNameForId(id: UShort): String?
    fun fieldIdForName(name: String): UShort?
    fun nextState(state: UShort, symbol: UShort): UShort
    fun lookaheadIterator(state: UShort): LookaheadIterator
    fun query(source: String): Query

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    companion object {
        val LANGUAGE_VERSION: UInt
        val MIN_COMPATIBLE_LANGUAGE_VERSION: UInt
    }
}
