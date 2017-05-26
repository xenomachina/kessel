// Copyright © 2017 Laurence Gonsalves
//
// This file is part of kessel, a library which can be found at
// http://github.com/xenomachina/kessel
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation; either version 2.1 of the License, or (at your
// option) any later version.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, see http://www.gnu.org/licenses/

package com.xenomachina.parser

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.FunSpec

// TODO does kotlintest have a matcher for this?
internal fun <T> Sequence<T>.shouldContain(vararg expected: T) {
    toList() shouldBe expected.toList()
}

sealed class Token {
    data class Identifier(override val value: String) : Token()
    data class Integer(override val value: String) : Token()
    data class Float(override val value: String) : Token()
    data class Space(override val value: String) : Token()
    abstract val value: String
}

class TokenizerTest : FunSpec({
    test("simple") {
        val tokenizer = Tokenizer<Int, Token>(
                CharOffsetTracker(),
                Regex("\\p{Alpha}[\\p{Alpha}0-9]+") to { m -> Token.Identifier(m.group()) },
                Regex("\\d+") to { m -> Token.Integer(m.group()) },
                Regex("\\d*\\.\\d") to { m -> Token.Float(m.group()) },
                Regex("\\s+") to { m -> Token.Space(m.group()) })

        tokenizer.tokenize("foo bar 123 baz789 45.6 45 .6 hello").shouldContain(
                Positioned(0, Token.Identifier("foo"), 3),
                Positioned(3, Token.Space(" "), 4),
                Positioned(4, Token.Identifier("bar"), 7),
                Positioned(7, Token.Space(" "), 8),
                Positioned(8, Token.Integer("123"), 11),
                Positioned(11, Token.Space(" "), 12),
                Positioned(12, Token.Identifier("baz789"), 18),
                Positioned(18, Token.Space(" "), 19),
                Positioned(19, Token.Float("45.6"), 23),
                Positioned(23, Token.Space(" "), 24),
                Positioned(24, Token.Integer("45"), 26),
                Positioned(26, Token.Space(" "), 27),
                Positioned(27, Token.Float(".6"), 29),
                Positioned(29, Token.Space(" "), 30),
                Positioned(30, Token.Identifier("hello"), 35)
        )
    }
})
