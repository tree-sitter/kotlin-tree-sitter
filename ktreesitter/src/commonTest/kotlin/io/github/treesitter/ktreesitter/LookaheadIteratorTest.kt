package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.java.language as java
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.*
import io.kotest.matchers.collections.*

class LookaheadIteratorTest : FunSpec({
    val language = Language(java())
    val program = language.symbolForName("program", true)
    val state = language.nextState(1U, program!!)
    val lookahead = language.lookaheadIterator(state)

    test("currentSymbol") {
        lookahead.currentSymbol shouldBe UShort.MAX_VALUE
        lookahead.currentSymbolName shouldBe "ERROR"
    }

    test("next()") {
        lookahead.next() shouldBe true
    }

    test("reset()") {
        lookahead.reset(state) shouldBe true
        lookahead.reset(state, language) shouldBe true
    }

    test("symbols()") {
        lookahead.symbols().shouldBeStrictlyIncreasing()
    }

    test("names()") {
        val names = lookahead.symbolNames().toList()
        names shouldContainExactly listOf("end", "line_comment", "block_comment")
    }

    test("iterator()") {
        for ((symbol, name) in lookahead) {
            symbol shouldBe lookahead.currentSymbol
            name shouldBe lookahead.currentSymbolName
        }
    }
})
