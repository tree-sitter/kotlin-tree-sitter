package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.java.language as java
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.*
import io.kotest.matchers.comparables.*
import io.kotest.matchers.string.*
import io.kotest.matchers.types.*

class LanguageTest : FunSpec({
    val language = Language(java())

    test("version") {
        language.version shouldBe 14U
    }

    test("symbolCount") {
        language.symbolCount shouldBeGreaterThan 1U
    }

    test("stateCount") {
        language.stateCount shouldBeGreaterThan 1U
    }

    test("fieldCount") {
        language.fieldCount shouldBeGreaterThan 1U
    }

    test("symbolName()") {
        language.symbolName(1U) shouldBe "identifier"
    }

    test("symbolForName()") {
        language.symbolForName(";", false) shouldNotBe null
        language.symbolForName("program", true) shouldNotBe null
    }

    test("isNamed()") {
        language.isNamed(1U) shouldBe true
    }

    test("isVisible()") {
        language.isVisible(1U) shouldBe true
    }

    test("fieldNameForId()") {
        language.fieldNameForId(1U) shouldNotBe null
    }

    test("fieldIdForName()") {
        language.fieldIdForName("body") shouldNotBe null
    }

    test("nextState()") {
        val program = language.symbolForName("program", true)
        language.nextState(1U, program!!) shouldBeGreaterThan 0U
    }

    test("lookaheadIterator()") {
        val program = language.symbolForName("program", true)
        val state = language.nextState(1U, program!!)
        val lookahead = language.lookaheadIterator(state)
        lookahead.language shouldBeSameInstanceAs language
    }

    xtest("query()") {
        shouldNotThrowAny { language.query("(program) @root") }
    }

    test("equals()") {
        Language(java()) shouldBe Language(java())
    }

    test("hashCode()") {
        language shouldHaveSameHashCodeAs java()
    }

    test("toString()") {
        language.toString() shouldMatch Regex("""Language\(id=0x[0-9a-z]+, version=14\)""")
    }
})
