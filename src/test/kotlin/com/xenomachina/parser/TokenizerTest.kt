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

sealed class MathToken {
    data class Identifier(override val value: String) : MathToken()
    data class Integer(override val value: String) : MathToken()
    data class Float(override val value: String) : MathToken()
    data class Space(override val value: String) : MathToken()
    data class MultOp(override val value: String) : MathToken()
    data class AddOp(override val value: String) : MathToken()
    data class OpenParen(override val value: String) : MathToken()
    data class CloseParen(override val value: String) : MathToken()
    abstract val value: String
}

val TEST_TOKENIZER = Tokenizer<Int, MathToken>(
        CharOffsetTracker(),
        Regex("\\p{Alpha}[\\p{Alpha}0-9]+") to { m -> MathToken.Identifier(m.group()) },
        Regex("\\d+") to { m -> MathToken.Integer(m.group()) },
        Regex("\\d*\\.\\d") to { m -> MathToken.Float(m.group()) },
        Regex("\\s+") to { m -> MathToken.Space(m.group()) },
        Regex("[*/]") to { m -> MathToken.MultOp(m.group()) },
        Regex("[-+]") to { m -> MathToken.AddOp(m.group()) },
        Regex("\\(") to { m -> MathToken.OpenParen(m.group()) },
        Regex("\\)") to { m -> MathToken.CloseParen(m.group()) }
)

class TokenizerTest : FunSpec({
    test("simple") {

        TEST_TOKENIZER.tokenize("foo bar 123 baz789 45.6 45 .6 hello").shouldContain(
                Positioned(0, MathToken.Identifier("foo"), 3),
                Positioned(3, MathToken.Space(" "), 4),
                Positioned(4, MathToken.Identifier("bar"), 7),
                Positioned(7, MathToken.Space(" "), 8),
                Positioned(8, MathToken.Integer("123"), 11),
                Positioned(11, MathToken.Space(" "), 12),
                Positioned(12, MathToken.Identifier("baz789"), 18),
                Positioned(18, MathToken.Space(" "), 19),
                Positioned(19, MathToken.Float("45.6"), 23),
                Positioned(23, MathToken.Space(" "), 24),
                Positioned(24, MathToken.Integer("45"), 26),
                Positioned(26, MathToken.Space(" "), 27),
                Positioned(27, MathToken.Float(".6"), 29),
                Positioned(29, MathToken.Space(" "), 30),
                Positioned(30, MathToken.Identifier("hello"), 35)
        )
    }
})
