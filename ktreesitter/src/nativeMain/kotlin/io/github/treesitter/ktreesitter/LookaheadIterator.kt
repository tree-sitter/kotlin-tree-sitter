package io.github.treesitter.ktreesitter

import cnames.structs.TSLanguage
import io.github.treesitter.ktreesitter.internal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class LookaheadIterator internal constructor(
    language: CValuesRef<TSLanguage>?,
    state: UShort
) : Iterator<UShort> {
    private val self = ts_lookahead_iterator_new(language, state)

    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val cleaner = createCleaner(self, ::ts_lookahead_iterator_delete)

    actual val language: Language
        get() = Language(ts_lookahead_iterator_language(self)!!)

    actual val currentSymbol: UShort
        get() = ts_lookahead_iterator_current_symbol(self)

    actual val currentSymbolName: String
        get() = ts_lookahead_iterator_current_symbol_name(self)!!.toKString()

    actual fun reset(state: UShort, language: Language?): Boolean {
        return if (language == null) {
            ts_lookahead_iterator_reset_state(self, state)
        } else {
            ts_lookahead_iterator_reset(self, language.self, state)
        }
    }

    override operator fun hasNext() = ts_lookahead_iterator_next(self)

    override operator fun next(): UShort {
        if (!hasNext()) throw NoSuchElementException()
        return ts_lookahead_iterator_current_symbol(self)
    }

    actual fun names(): Iterator<String> = object : Iterator<String> {
        override operator fun hasNext() = ts_lookahead_iterator_next(self)

        override operator fun next(): String {
            if (!hasNext()) throw NoSuchElementException()
            return ts_lookahead_iterator_current_symbol_name(self)!!.toKString()
        }
    }
}
