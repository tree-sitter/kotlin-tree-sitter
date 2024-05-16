package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.internal.TSInputEncoding
import kotlinx.cinterop.ExperimentalForeignApi

actual enum class InputEncoding {
    UTF8,
    UTF16;

    @OptIn(ExperimentalForeignApi::class)
    internal fun convert() = when (this) {
        UTF8 -> TSInputEncoding.TSInputEncodingUTF8
        UTF16 -> TSInputEncoding.TSInputEncodingUTF16
    }
}
