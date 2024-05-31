package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.java.language as java
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.*
import io.kotest.matchers.collections.*
import io.kotest.matchers.nulls.*
import io.kotest.matchers.types.*

class TreeTest : FunSpec({
    val language = Language(java())
    val parser = Parser(language)
    var source = "class Foo {}"
    var tree = parser.parse(source)

    test("source") {
        tree.source shouldBe source
    }

    test("language") {
        tree.language shouldBeSameInstanceAs language
    }

    test("rootNode") {
        tree.rootNode.endByte shouldBe source.length.toUInt()
    }

    test("includedRanges") {
        tree.includedRanges.shouldHaveSingleElement {
            it.startByte == 0U && it.endByte == UInt.MAX_VALUE
        }
    }

    test("rootNodeWithOffset()") {
        val node = tree.rootNodeWithOffset(6U, Point(0U, 6U))
        node?.text() shouldBe "Foo {}"
    }

    test("edit()") {
        val edit = InputEdit(9U, 9U, 10U, Point(0U, 9U), Point(0U, 9U), Point(0U, 10U))
        source = "class Foo2 {}"
        tree.edit(edit)
        tree.source.shouldBeNull()
        tree = parser.parse(source, tree)
        tree.source shouldBe source
    }

    test("walk()") {
        val cursor = tree.walk()
        cursor.tree shouldBeSameInstanceAs tree
    }

    test("text()") {
        tree.text() shouldBe source
    }

    test("changedRanges()") {
        val edit = InputEdit(0U, 0U, 7U, Point(0U, 0U), Point(0U, 0U), Point(0U, 7U))
        tree.edit(edit)
        val newTree = parser.parse("public $source", tree)
        tree.changedRanges(newTree).shouldHaveSingleElement {
            it.endByte == 7U && it.endPoint.column == 7U
        }
    }
})