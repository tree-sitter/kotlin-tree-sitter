package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.java.language as java
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forSome
import io.kotest.matchers.*
import io.kotest.matchers.collections.*
import io.kotest.matchers.nulls.*
import io.kotest.matchers.string.*
import io.kotest.matchers.types.*

class ParserTest : FunSpec({
    val language = Language(java())
    val parser = Parser(language)

    test("language") {
        parser.language shouldBeSameInstanceAs language
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

    test("logger") {
        parser.logger shouldBe null
        parser.logger = { _, _ -> }
        parser.logger shouldNotBe null
    }

    test("parse(source)") {
        // UTF-8
        var source = "class Foo {}"
        var tree = parser.parse(source)
        tree.source?.get(5) shouldBe ' '

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
            source.substring(byte.toInt(), minOf(byte.toInt() + 1, source.length))
        }
        tree.text().shouldBeNull()
        tree.rootNode.type shouldBe "program"
    }

    afterTest { (test, _) ->
        when (test.name.testName) {
            "includedRanges" -> parser.includedRanges = emptyList()
            "timeoutMicros" -> parser.timeoutMicros = 0UL
            "logger" -> parser.logger = null
        }
    }
})
