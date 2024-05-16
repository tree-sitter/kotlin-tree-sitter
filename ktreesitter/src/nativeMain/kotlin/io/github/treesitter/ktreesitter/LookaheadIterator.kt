package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.internal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class LookaheadIterator internal constructor(
    actual val language: Language,
    private val state: UShort
) : Iterator<UShort> {
    private val self = ts_lookahead_iterator_new(language.self, state)

    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val cleaner = createCleaner(self, ::ts_lookahead_iterator_delete)

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

    // FIXME: don't advance iterator
    override operator fun hasNext() = ts_lookahead_iterator_next(self)

    override operator fun next(): UShort {
        if (!hasNext()) throw NoSuchElementException()
        return ts_lookahead_iterator_current_symbol(self)
    }

    actual fun names(): Iterator<String> = object : Iterator<String> {
        private val self = ts_lookahead_iterator_new(language.self, state)

        @Suppress("unused")
        @OptIn(ExperimentalNativeApi::class)
        private val cleaner = createCleaner(self, ::ts_lookahead_iterator_delete)

        // FIXME: don't advance iterator
        override operator fun hasNext() = ts_lookahead_iterator_next(self)

        override operator fun next(): String {
            if (!hasNext()) throw NoSuchElementException()
            return ts_lookahead_iterator_current_symbol_name(self)!!.toKString()
        }
    }
}