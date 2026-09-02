package io.github.treesitter.ktreesitter

import io.github.treesitter.ktreesitter.java.TreeSitterJava
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ParserEncodingTest : FunSpec({
    val parser = Parser().apply { language = Language(TreeSitterJava.language()) }
    val source = "class Foo { String s = \"🎉🎉\"; }"

    test("parse(source) encodes astral characters as UTF-8") {
        val tree = parser.parse(source)
        tree.rootNode.hasError shouldBe false
        tree.rootNode.endByte shouldBe source.encodeToByteArray().size.toUInt()
    }

    test("parse(readCallback) encodes astral characters as UTF-8") {
        val tree = parser.parse { byte, _ -> if (byte == 0U) source else null }
        tree.rootNode.hasError shouldBe false
        tree.rootNode.endByte shouldBe source.encodeToByteArray().size.toUInt()
    }

    test("parse(source) encodes UTF-16 input as UTF-16") {
        val tree = parser.parse(source, encoding = InputEncoding.UTF_16LE)
        tree.rootNode.hasError shouldBe false
        tree.rootNode.endByte shouldBe (source.length * 2).toUInt()
    }
})
