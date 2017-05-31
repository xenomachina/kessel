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

sealed class TestToken {
    data class Identifier(override val value: String) : TestToken()
    data class Integer(override val value: String) : TestToken()
    data class Float(override val value: String) : TestToken()
    data class Space(override val value: String) : TestToken()
    data class MultOp(override val value: String) : TestToken()
    data class AddOp(override val value: String) : TestToken()
    data class OpenParen(override val value: String) : TestToken()
    data class CloseParen(override val value: String) : TestToken()
    abstract val value: String
}

val TEST_TOKENIZER = Tokenizer<Int, TestToken>(
        CharOffsetTracker(),
        Regex("\\p{Alpha}[\\p{Alpha}0-9]+") to { m -> TestToken.Identifier(m.group()) },
        Regex("\\d+") to { m -> TestToken.Integer(m.group()) },
        Regex("\\d*\\.\\d") to { m -> TestToken.Float(m.group()) },
        Regex("\\s+") to { m -> TestToken.Space(m.group()) },
        Regex("[*/]") to { m -> TestToken.MultOp(m.group()) },
        Regex("[-+]") to { m -> TestToken.AddOp(m.group()) },
        Regex("\\(") to { m -> TestToken.OpenParen(m.group()) },
        Regex("\\)") to { m -> TestToken.CloseParen(m.group()) }
)

class TokenizerTest : FunSpec({
    test("simple") {

        TEST_TOKENIZER.tokenize("foo bar 123 baz789 45.6 45 .6 hello").shouldContain(
                Positioned(0, TestToken.Identifier("foo"), 3),
                Positioned(3, TestToken.Space(" "), 4),
                Positioned(4, TestToken.Identifier("bar"), 7),
                Positioned(7, TestToken.Space(" "), 8),
                Positioned(8, TestToken.Integer("123"), 11),
                Positioned(11, TestToken.Space(" "), 12),
                Positioned(12, TestToken.Identifier("baz789"), 18),
                Positioned(18, TestToken.Space(" "), 19),
                Positioned(19, TestToken.Float("45.6"), 23),
                Positioned(23, TestToken.Space(" "), 24),
                Positioned(24, TestToken.Integer("45"), 26),
                Positioned(26, TestToken.Space(" "), 27),
                Positioned(27, TestToken.Float(".6"), 29),
                Positioned(29, TestToken.Space(" "), 30),
                Positioned(30, TestToken.Identifier("hello"), 35)
        )
    }
})
