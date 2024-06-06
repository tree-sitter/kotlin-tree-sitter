package io.github.treesitter.ktreesitter.java

import java.lang.NullPointerException

actual object TreeSitterJava {
    private const val LIB_NAME = "ktreesitter-java"

    init {
        try {
            System.loadLibrary(LIB_NAME)
        } catch (ex: UnsatisfiedLinkError) {
            try {
                @Suppress("UnsafeDynamicallyLoadedCode")
                System.load(libPath())
            } catch (_: UnsatisfiedLinkError) {
                throw ex
            } catch (_: NullPointerException) {
                throw ex
            }
        }
    }

    actual fun language(): Any = nativeLanguage()

    @JvmStatic
    @Throws(UnsupportedOperationException::class)
    private fun libPath(): String? {
        val osName = System.getProperty("os.name")!!.lowercase()
        val archName = System.getProperty("os.arch")!!.lowercase()
        val ext: String
        val os: String
        val prefix: String
        when {
            "windows" in osName -> {
                ext = "dll"
                os = "windows"
                prefix = ""
            }
            "linux" in osName -> {
                ext = "so"
                os = "linux"
                prefix = "lib"
            }
            "mac" in osName -> {
                ext = "dylib"
                os = "macos"
                prefix = "lib"
            }
            else -> {
                throw UnsupportedOperationException("Unsupported operating system: $osName")
            }
        }
        val arch = when {
            "amd64" in archName || "x86_64" in archName -> "x86_64"
            "aarch64" in archName -> "aarch64"
            else -> throw UnsupportedOperationException("Unsupported architecture: $archName")
        }
        val libPath = "/lib/$os/$arch/$prefix$LIB_NAME.$ext"
        return javaClass.classLoader.getResource(libPath)?.path
    }

    @JvmStatic
    private external fun nativeLanguage(): Long
}
