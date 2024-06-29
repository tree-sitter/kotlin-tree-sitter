package io.github.treesitter.ktreesitter.dsl

import kotlin.js.json

class Grammar private constructor(
    private var name: String,
    private val inherits: String? = null,
    var word: Symbol? = null,
    var extras: Array<Rule> = arrayOf(),
    private val rules: MutableMap<String, Rule> = mutableMapOf()
) {
    internal constructor(name: String, callback: Grammar.() -> Unit) : this(name) {
        callback(this)
    }

    internal constructor(name: String, parent: Grammar, callback: Grammar.() -> Unit) : this(
        name,
        parent.name,
        parent.word,
        parent.extras,
        parent.rules
    ) {
        callback(this)
    }

    init {
        check(rules.isNotEmpty()) { "Grammar must have at least one rule." }
    }

    fun rules(): Unit = TODO("")

    override fun toString(): String {
        val data = json(
            "name" to name,
            "inherits" to (inherits ?: undefined),
            "word" to (word?.name ?: undefined),
            "rules" to rules,
            "extras" to extras.ifEmpty { undefined },
            "conflicts" to conflicts.ifEmpty { undefined },
            "precedences" to precedences.ifEmpty { undefined },
            "externals" to externals.ifEmpty { undefined },
            "inline" to inline.ifEmpty { undefined },
            "supertypes" to supertypes.ifEmpty { undefined }
        )
        return JSON.stringify(data)
    }
}
