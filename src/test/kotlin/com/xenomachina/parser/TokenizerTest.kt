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
    data class Identifier(val name: String) : MathToken()
    data class Integer(val value: Int) : MathToken()
    data class Float(val value: Double) : MathToken()
    data class Space(val spaces: String) : MathToken()
    data class MultOp(val name: String) : MathToken()
    data class AddOp(val name: String) : MathToken()

    object OpenParen : MathToken()
    object CloseParen : MathToken()
}

val TEST_TOKENIZER = RegexTokenizer<Int, MathToken>(
    CharOffsetTracker(),
    Regex("\\p{Alpha}[\\p{Alpha}0-9]+") to { m -> MathToken.Identifier(m.group()) },
    Regex("\\d+") to { m -> MathToken.Integer(m.group().toInt()) },
    Regex("\\d*\\.\\d") to { m -> MathToken.Float(m.group().toDouble()) },
    Regex("\\s+") to { m -> MathToken.Space(m.group()) },
    Regex("[*/]") to { m -> MathToken.MultOp(m.group()) },
    Regex("[-+]") to { m -> MathToken.AddOp(m.group()) },
    Regex("\\(") to { _ -> MathToken.OpenParen },
    Regex("\\)") to { _ -> MathToken.CloseParen }
)

class TokenizerTest : FunSpec({
    test("simple") {

        TEST_TOKENIZER.tokenize("foo bar 123 baz789 45.6 45 .6 hello").shouldContain(
                Positioned(0, MathToken.Identifier("foo"), 3),
                Positioned(3, MathToken.Space(" "), 4),
                Positioned(4, MathToken.Identifier("bar"), 7),
                Positioned(7, MathToken.Space(" "), 8),
                Positioned(8, MathToken.Integer(123), 11),
                Positioned(11, MathToken.Space(" "), 12),
                Positioned(12, MathToken.Identifier("baz789"), 18),
                Positioned(18, MathToken.Space(" "), 19),
                Positioned(19, MathToken.Float(45.6), 23),
                Positioned(23, MathToken.Space(" "), 24),
                Positioned(24, MathToken.Integer(45), 26),
                Positioned(26, MathToken.Space(" "), 27),
                Positioned(27, MathToken.Float(.6), 29),
                Positioned(29, MathToken.Space(" "), 30),
                Positioned(30, MathToken.Identifier("hello"), 35)
        )
    }
})
