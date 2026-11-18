package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.java.TreeSitterJava
import io.kotest.assertions.throwables.shouldNotThrowAnyUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forSome
import io.kotest.matchers.*
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.*
import io.kotest.matchers.nulls.*
import io.kotest.matchers.string.*
import io.kotest.matchers.types.*

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

        source = "class Foo { String s = \"café 日本語\"; }"
        tree = parser.parse(source)
        tree.rootNode.endByte shouldBe source.encodeToByteArray().size.toUInt()

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
        source = "\uFEFFvar java = \"💩\";"
        tree = parser.parse(source, encoding = InputEncoding.UTF_16BE)
        tree.text()?.subSequence(13, 15) shouldBe "\uD83D\uDCA9"
        tree.rootNode.hasError.shouldBeFalse()
        tree.rootNode.endByte shouldBe (source.length * 2).toUInt()
    }

    test("parse(readCallback)") {
        var source = "class Foo {}"
        var tree = parser.parse { byte, _ ->
            val end = minOf(byte.toInt() * 2, source.length)
            source.subSequence(byte.toInt(), end).ifEmpty { null }
        }
        tree.text().shouldBeNull()
        tree.rootNode.type shouldBe "program"

        source = "class Foo { String s = \"🎉🎉\"; }"
        tree = parser.parse { byte, _ -> if (byte == 0U) source else null }
        tree.rootNode.hasError.shouldBeFalse()
        tree.rootNode.endByte shouldBe source.encodeToByteArray().size.toUInt()
    }

    afterTest { (test, _) ->
        when (test.name.name) {
            "includedRanges" -> parser.includedRanges = emptyList()
            "logger" -> parser.logger = null
        }
    }
})
