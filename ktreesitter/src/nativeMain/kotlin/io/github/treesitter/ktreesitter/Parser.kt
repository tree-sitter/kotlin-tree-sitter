package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.internal.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class Parser actual constructor() {
    actual constructor(language: Language) : this() {
        this.language = language
    }

    private val self = ts_parser_new()

    actual var language: Language? = null
        set(value) {
            ts_parser_set_language(self, value?.self)
            field = value
        }

    actual var includedRanges: List<Range> = emptyList()
        /* get() = memScoped {
            val length = alloc<UIntVar>()
            val ranges = ts_parser_included_ranges(self, length.ptr) ?: return emptyList()
            return List(length.value.convert()) { ranges[it].convert() }
        } */
        set(value) {
            val size = value.size
            if (size > 0) {
                val arena = Arena()
                val ranges = arena.allocArray<TSRange>(size) {
                    arena.alloc<TSRange>().from(value[it])
                }
                val result = ts_parser_set_included_ranges(self, ranges, size.convert())
                arena.clear()
                require(result) { "Included ranges cannot overlap" }
            } else {
                ts_parser_set_included_ranges(self, null, 0U)
            }
            field = value
        }

    actual var timeoutMicros: ULong
        get() = ts_parser_timeout_micros(self)
        set(value) = ts_parser_set_timeout_micros(self, value)

    /*
    private val nativeCancellationFlag = nativeHeap.alloc<ULongVar>()

    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val flagCleaner = createCleaner(nativeCancellationFlag, nativeHeap::free)

    @Volatile
    actual var cancellationFlag: ULong?
        get() = ts_parser_cancellation_flag(self)?.pointed?.value
        set(value) {
            if (value == null) {
                ts_parser_set_cancellation_flag(self, null)
            } else {
                nativeCancellationFlag.value = value
                ts_parser_set_cancellation_flag(self, nativeCancellationFlag.ptr)
            }
        }
     */

    actual fun parse(source: String, oldTree: Tree?, encoding: InputEncoding): Tree {
        val tree = ts_parser_parse_string_encoding(
            self,
            oldTree?.self,
            source,
            source.length.convert(),
            encoding.convert()
        )
        return Tree(tree, source)
    }

    actual fun parse(callback: ParseCallback, oldTree: Tree?, encoding: InputEncoding): Tree {
        val input = cValue<TSInput> {
            this.encoding = encoding.convert()
            TODO("set read, payload")
        }
        val tree = ts_parser_parse(self, oldTree?.self, input)
        return Tree(tree, null)
    }

    actual fun reset() = ts_parser_reset(self)
}
