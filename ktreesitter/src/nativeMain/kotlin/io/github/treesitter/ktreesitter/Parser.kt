package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.internal.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class Parser actual constructor() {
    actual constructor(language: Language) : this() {
        this.language = language
    }

    private val self = ts_parser_new()

    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val cleaner = createCleaner(self, ::ts_parser_delete)

    actual var language: Language? = null
        set(value) {
            ts_parser_set_language(self, value?.self)
            field = value
        }

    actual var includedRanges: List<Range> = emptyList()
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

    actual var logger: LogFunction? = null
        set(value) {
            if (field != null) {
                val arena = Arena()
                with(ts_parser_logger(self)) {
                    interpretNullablePointed<TSLogger>(
                        arena.alloc(size, align).rawPtr
                    )?.payload?.asStableRef<TSLogger>()?.dispose()
                }
                arena.clear()
            }
            if (value != null) {
                val logger = cValue<TSLogger> {
                    payload = StableRef.create(value).asCPointer()
                    log = staticCFunction { payload, type, message ->
                        val callback = payload?.asStableRef<LogFunction>()?.get()
                        if (callback != null && message != null) {
                            val logType = when (type) {
                                TSLogType.TSLogTypeLex -> LogType.LEX
                                TSLogType.TSLogTypeParse -> LogType.PARSE
                                else -> error("Unreachable")
                            }
                            callback(logType, message.toKString())
                        }
                    }
                }
                ts_parser_set_logger(self, logger)
            }
            field = value
        }

    actual fun parse(source: String, oldTree: Tree?): Tree {
        val tree = ts_parser_parse_string(
            self,
            oldTree?.self,
            source,
            source.length.convert()
        )
        return Tree(tree, source)
    }

    actual fun parse(oldTree: Tree?, callback: ParseCallback): Tree {
        val arena = Arena()
        val payloadRef = StableRef.create(ParsePayload(arena, callback))
        val input = cValue<TSInput> {
            payload = payloadRef.asCPointer()
            encoding = TSInputEncodingUTF8
            read = staticCFunction { payload, index, point, bytes ->
                val data = payload!!.asStableRef<ParsePayload>().get()
                val result = data.callback(index, point.useContents { convert() })
                if (result != null) data.value += result
                bytes!!.pointed.value = result?.length?.convert() ?: 0U
                result?.cstr?.getPointer(data.memScope)
            }
        }
        val tree = ts_parser_parse(self, oldTree?.self, input)
        arena.clear()
        payloadRef.dispose()
        return Tree(tree, null)
    }

    actual fun reset() = ts_parser_reset(self)

    actual enum class LogType { LEX, PARSE }

    private class ParsePayload(
        val memScope: AutofreeScope,
        val callback: ParseCallback,
        var value: String = ""
    )
}
