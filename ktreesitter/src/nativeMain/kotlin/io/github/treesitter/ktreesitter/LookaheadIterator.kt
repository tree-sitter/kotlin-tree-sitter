package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.internal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class LookaheadIterator internal constructor(
    actual val language: Language,
    private val state: UShort
) : Iterable<LookaheadIterator.Symbol> {
    private val self = ts_lookahead_iterator_new(language.self, state)

    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val cleaner = createCleaner(self, ::ts_lookahead_iterator_delete)

    actual val currentSymbol: UShort
        get() = ts_lookahead_iterator_current_symbol(self)

    actual val currentSymbolName: String?
        get() = ts_lookahead_iterator_current_symbol_name(self)?.toKString()

    actual fun reset(state: UShort, language: Language?): Boolean {
        return if (language == null) {
            ts_lookahead_iterator_reset_state(self, state)
        } else {
            ts_lookahead_iterator_reset(self, language.self, state)
        }
    }

    actual fun next() = ts_lookahead_iterator_next(self)

    actual fun symbols(): Sequence<UShort> {
        ts_lookahead_iterator_reset_state(self, state)
        return sequence {
            while (ts_lookahead_iterator_next(self))
                yield(ts_lookahead_iterator_current_symbol(self))
        }
    }

    actual fun symbolNames(): Sequence<String?> {
        ts_lookahead_iterator_reset_state(self, state)
        return sequence {
            while (ts_lookahead_iterator_next(self))
                yield(ts_lookahead_iterator_current_symbol_name(self)?.toKString())
        }
    }

    override operator fun iterator(): Iterator<Symbol> {
        ts_lookahead_iterator_reset_state(self, state)
        return iterator {
            while (ts_lookahead_iterator_next(self)) {
                val id = ts_lookahead_iterator_current_symbol(self)
                val name = ts_lookahead_iterator_current_symbol_name(self)?.toKString()
                yield(Symbol(id, name))
            }
        }
    }

    actual data class Symbol actual constructor(actual val id: UShort, actual val name: String?)
}
