package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.java.TreeSitterJava
import io.kotest.assertions.throwables.shouldNotThrowAnyUnit
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.Platform
import io.kotest.common.platform
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forSome
import io.kotest.matchers.*
import io.kotest.matchers.collections.*
import io.kotest.matchers.nulls.*
import io.kotest.matchers.string.*
import io.kotest.matchers.types.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

class ParserTest : FunSpec({
    val parser = Parser()

    test("language") {
        val language = Language(TreeSitterJava.language())
        parser.language.shouldBeNull()
        parser.language = language
        parser.language shouldBeSameInstanceAs language
    }

    test("includedRanges") {
        val range = Range(Point(0U, 0U), Point(0U, 1U), 0U, 1U)
        parser.includedRanges.shouldBeEmpty()
        parser.includedRanges = listOf(range)
        parser.includedRanges shouldHaveSingleElement range
    }

    test("timeoutMicros") {
        parser.timeoutMicros shouldBe 0UL
        parser.timeoutMicros = 10000UL
        parser.timeoutMicros shouldBe 10000UL
    }

    test("cancellationFlag") {
        parser.cancellationFlag shouldBe 0UL
        parser.cancellationFlag = 10UL
        parser.cancellationFlag shouldBe 10UL
    }

    test("logger") {
        shouldNotThrowAnyUnit {
            parser.logger = { _, _ ->
                throw UnsupportedOperationException()
            }
        }
    }

    test("parse(source)") {
        // UTF-8
        var source = "class Foo {}"
        var tree = parser.parse(source)
        tree.text()?.get(5) shouldBe ' '

        // logging
        val logs = mutableListOf<String>()
        parser.logger = { type, msg -> logs += "$type - $msg" }
        parser.reset()
        parser.parse(source)
        parser.logger = null
        logs.shouldNotBeEmpty().forSome {
            it shouldStartWith "LEX"
        }

        // UTF-16
        source = "var java = \"💩\""
        tree = parser.parse(source)
        tree.text()?.subSequence(12, 14) shouldBe "\uD83D\uDCA9"
    }

    test("parse(callback)") {
        val source = "class Foo {}"
        val tree = parser.parse { byte, _ ->
            source.subSequence(byte.toInt(), minOf(byte.toInt() + 1, source.length))
        }
        tree.text().shouldBeNull()
        tree.rootNode.type shouldBe "program"

        // Timeout
        parser.timeoutMicros = 1000UL
        shouldThrow<IllegalStateException> {
            parser.parse { _, _ -> "{" }
        }
        parser.reset()
        parser.timeoutMicros = 0UL

        // FIXME: the second parse crashes on JVM
        if (platform == Platform.JVM) return@test

        // Cancellation flag
        coroutineScope {
            val parse = async {
                shouldThrow<IllegalStateException> {
                    parser.parse { byte, _ ->
                        if (byte == 0U) "int[] foo = {" else "0,"
                    }
                }
            }.apply { start() }
            val cancel = async {
                delay(1000L)
                parser.cancellationFlag = 1U
            }.apply { start() }
            awaitAll(parse, cancel)

            var finished = false
            parser.cancellationFlag = 0U
            parser.parse { _, _ ->
                if (finished) {
                    null
                } else {
                    finished = true
                    "0};"
                }
            }.rootNode.hasError shouldBe false
        }
    }

    afterTest { (test, _) ->
        when (test.name.testName) {
            "includedRanges" -> parser.includedRanges = emptyList()
            "timeoutMicros" -> parser.timeoutMicros = 0UL
            "cancellationFlag" -> parser.timeoutMicros = 0UL
            "logger" -> parser.logger = null
        }
    }
})
