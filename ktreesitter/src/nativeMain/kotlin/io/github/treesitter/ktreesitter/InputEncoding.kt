package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.internal.TSInputEncoding
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * The encoding of the input text.
 *
 * @since 0.25.0
 */
@Suppress("unused")
@OptIn(ExperimentalForeignApi::class)
actual enum class InputEncoding(internal val value: TSInputEncoding) {
    UTF_8(TSInputEncoding.TSInputEncodingUTF8),
    UTF_16LE(TSInputEncoding.TSInputEncodingUTF16LE),
    UTF_16BE(TSInputEncoding.TSInputEncodingUTF16BE);

    internal fun encode(text: String): ByteArray {
        if (this == UTF_8) return text.encodeToByteArray()
        val high = if (this == UTF_16BE) 0 else 1
        val bytes = ByteArray(text.length * 2)
        for (i in text.indices) {
            val code = text[i].code
            bytes[2 * i + high] = (code shr 8).toByte()
            bytes[2 * i + 1 - high] = code.toByte()
        }
        return bytes
    }

    internal fun decode(bytes: ByteArray, start: Int, end: Int): String {
        if (this == UTF_8) return bytes.decodeToString(start, end)
        val high = if (this == UTF_16BE) 0 else 1
        return CharArray((end - start) / 2) {
            val i = start + 2 * it
            val hi = bytes[i + high].toInt() and 0xFF
            val lo = bytes[i + 1 - high].toInt() and 0xFF
            ((hi shl 8) or lo).toChar()
        }.concatToString()
    }
}
