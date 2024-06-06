package io.github.treesitter.ktreesitter.java

import dalvik.annotation.optimization.CriticalNative

actual object TreeSitterJava {
    init {
        System.loadLibrary("ktreesitter-java")
    }

    actual fun language(): Any = nativeLanguage()

    @JvmStatic
    @CriticalNative
    private external fun nativeLanguage(): Long
}
