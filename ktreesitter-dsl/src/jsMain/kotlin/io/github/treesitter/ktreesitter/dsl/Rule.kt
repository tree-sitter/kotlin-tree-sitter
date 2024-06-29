package io.github.treesitter.ktreesitter.dsl

import kotlin.js.RegExp
import kotlin.js.json

sealed class Rule(protected val type: String) {
    internal open fun normalize() = json("type" to type)

    final override fun toString() = JSON.stringify(normalize())
}

class Alias<T : Rule> internal constructor(
    rule: Rule,
    private val named: Boolean,
    private val value: T
) : Rule("ALIAS") {
    private val content = rule.normalize()

    override fun normalize() = json(
        "type" to type,
        "content" to content,
        "named" to named,
        "value" to value
    )
}

class Blank internal constructor() : Rule("BLANK")

class Field internal constructor(private val name: String, rule: Rule) : Rule("FIELD") {
    private val content = rule.normalize()

    override fun normalize() = json(
        "type" to type,
        "name" to name,
        "content" to content
    )
}

class Choice internal constructor(elements: Array<out Rule>) : Rule("CHOICE") {
    private val members = elements.map(Rule::normalize)

    override fun normalize() = json("type" to type, "members" to members)
}

class Prec internal constructor(private val number: Int, rule: Rule) : Rule("PREC") {
    private val content = rule.normalize()

    override fun normalize() = json(
        "type" to type,
        "value" to number,
        "content" to content
    )

    class Left internal constructor(private val number: Int, rule: Rule) : Rule("PREC_LEFT") {
        private val content = rule.normalize()

        override fun normalize() = json(
            "type" to type,
            "value" to number,
            "content" to content
        )
    }

    class Right internal constructor(private val number: Int, rule: Rule) : Rule("PREC_RIGHT") {
        private val content = rule.normalize()

        override fun normalize() = json(
            "type" to type,
            "value" to number,
            "content" to content
        )
    }

    class Dynamic internal constructor(private val number: Int, rule: Rule) : Rule("PREC_DYNAMIC") {
        private val content = rule.normalize()

        override fun normalize() = json(
            "type" to type,
            "value" to number,
            "content" to content
        )
    }
}

class Repeat internal constructor(rule: Rule) : Rule("REPEAT") {
    private val content = rule.normalize()

    override fun normalize() = json("type" to type, "content" to content)
}

class Repeat1 internal constructor(rule: Rule) : Rule("REPEAT1") {
    private val content = rule.normalize()

    override fun normalize() = json("type" to type, "content" to content)
}

class Seq internal constructor(elements: Array<out Rule>) : Rule("SEQ") {
    private val members = elements.map(Rule::normalize)

    override fun normalize() = json("type" to type, "members" to members)
}

class Symbol internal constructor(internal val name: String) : Rule("SYMBOL") {
    override fun normalize() = json("type" to type, "name" to name)
}

class Token internal constructor(value: Rule) : Rule("TOKEN") {
    private val content = value.normalize()

    override fun normalize() = json("type" to type, "content" to content)

    class Immediate internal constructor(value: Rule) : Rule("TOKEN_IMMEDIATE") {
        private val content = value.normalize()

        override fun normalize() = json("type" to type, "content" to content)
    }
}

sealed class Literal<T : Any>(type: kotlin.String, protected val value: T) : Rule(type) {
    class String(value: kotlin.String) : Literal<kotlin.String>("STRING", value) {
        override fun normalize() = json("type" to type, "value" to value)
    }

    class Pattern(value: RegExp) : Literal<RegExp>("PATTERN", value) {
        private val source = value.asDynamic().source as kotlin.String
        private val flags = value.asDynamic().flags as kotlin.String

        override fun normalize() = if (flags == "") {
            json("type" to type, "value" to source)
        } else {
            json("type" to type, "value" to source, "flags" to flags)
        }
    }
}
