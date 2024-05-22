package io.github.treesitter.ktreesitter

import cnames.structs.TSLanguage
import io.github.treesitter.ktreesitter.internal.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class Language(language: Any) {
    internal val self: CPointer<TSLanguage> =
        (language as? CPointer<*>)?.rawValue?.let(::interpretCPointer)
            ?: throw IllegalArgumentException("Invalid language: $language")

    actual val version = ts_language_version(self)

    init {
        check(version in MIN_COMPATIBLE_LANGUAGE_VERSION..LANGUAGE_VERSION) {
            "Incompatible language version $version. " +
                "Must be between $MIN_COMPATIBLE_LANGUAGE_VERSION and $LANGUAGE_VERSION."
        }
    }

    actual val symbolCount = ts_language_symbol_count(self)

    actual val stateCount = ts_language_state_count(self)

    actual val fieldCount = ts_language_field_count(self)

    actual fun symbolName(symbol: UShort) = ts_language_symbol_name(self, symbol)?.toKString()

    actual fun symbolForName(name: String, isNamed: Boolean): UShort? =
        ts_language_symbol_for_name(self, name, name.length.convert(), isNamed).takeIf { it > 0U }

    actual fun isNamed(symbol: UShort) =
        ts_language_symbol_type(self, symbol) == TSSymbolTypeRegular

    actual fun isVisible(symbol: UShort) =
        ts_language_symbol_type(self, symbol) <= TSSymbolTypeAnonymous

    actual fun fieldNameForId(id: UShort) = ts_language_field_name_for_id(self, id)?.toKString()

    actual fun fieldIdForName(name: String): UShort? =
        ts_language_field_id_for_name(self, name, name.length.convert()).takeIf { it > 0U }

    actual fun nextState(state: UShort, symbol: UShort): UShort =
        ts_language_next_state(self, state, symbol)

    actual fun lookaheadIterator(state: UShort) = LookaheadIterator(this, state)

    actual fun query(source: String) = Query(this, source)

    actual override fun equals(other: Any?) =
        this === other || (other is Language && self == other.self)

    actual override fun hashCode() = self.hashCode()

    override fun toString() = "Language(id=${self.rawValue}, version=$version)"

    actual companion object {
        actual val LANGUAGE_VERSION: UInt = TREE_SITTER_LANGUAGE_VERSION.convert()
        actual val MIN_COMPATIBLE_LANGUAGE_VERSION: UInt =
            TREE_SITTER_MIN_COMPATIBLE_LANGUAGE_VERSION.convert()
    }
}
