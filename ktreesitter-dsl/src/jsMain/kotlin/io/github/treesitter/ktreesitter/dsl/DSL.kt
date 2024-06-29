package io.github.treesitter.ktreesitter.dsl

import kotlin.js.RegExp

@DslMarker
internal annotation class DSLFunction

@DSLFunction
fun alias(rule: Rule, value: string) = Alias(rule, false, value)

@DSLFunction
fun alias(rule: Rule, value: Symbol) = Alias(rule, true, value)

@DSLFunction
fun blank() = Blank()

@DSLFunction
fun field(name: String, rule: Rule) = Field(name, rule)

@DSLFunction
fun choice(vararg elements: Rule) = Choice(elements)

@DSLFunction
fun optional(value: Rule) = choice(value, blank())

@DSLFunction
fun pattern(value: RegExp) = Literal.Pattern(value)

@DSLFunction
fun prec(number: Int, rule: Rule) = Prec(number, rule)

object prec {
    @DSLFunction
    fun left(number: Int, rule: Rule) = Prec.Left(number, rule)

    @DSLFunction
    fun left(rule: Rule) = Prec.Left(0, rule)

    @DSLFunction
    fun right(number: Int, rule: Rule) = Prec.Right(number, rule)

    @DSLFunction
    fun right(rule: Rule) = Prec.Right(0, rule)
}

@DSLFunction
fun repeat(rule: Rule) = Repeat(rule)

@DSLFunction
fun repeat1(rule: Rule) = Repeat1(rule)

@DSLFunction
fun seq(vararg elements: Rule) = Seq(elements)

@DSLFunction
fun string(value: String) = Literal.String(value)

@DSLFunction
fun sym(name: String) = Symbol(name)

@DSLFunction
fun token(value: Rule) = Token(value)

object token {
    @DSLFunction
    fun immediate(value: Rule) = Token.Immediate(value)
}

fun grammar(name: String, block: Grammar.() -> Unit) = Grammar(name, block)

fun grammar(name: String, parent: Grammar, block: Grammar.() -> Unit) = Grammar(name, parent, block)
