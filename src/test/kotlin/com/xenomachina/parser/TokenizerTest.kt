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
    sealed class Value : MathToken() {
        data class Identifier(val name: String) : MathToken.Value()
        data class IntLiteral(val value: Int) : MathToken.Value()
        data class FloatLiteral(val value: Double) : MathToken.Value()
    }

    sealed class Operator : MathToken() {
        data class MultOp(val name: String) : MathToken.Operator()
        data class AddOp(val name: String) : MathToken.Operator()
    }

    object OpenParen : MathToken()
    object CloseParen : MathToken()

    data class Space(val spaces: String) : MathToken()
}

val MATH_TOKENIZER = RegexTokenizer<MathToken>(
    Regex("\\p{Alpha}[\\p{Alpha}0-9]+") to { m -> MathToken.Value.Identifier(m.group()) },
    Regex("\\d+") to { m -> MathToken.Value.IntLiteral(m.group().toInt()) },
    Regex("\\d*\\.\\d") to { m -> MathToken.Value.FloatLiteral(m.group().toDouble()) },
    Regex("\\s+") to { m -> MathToken.Space(m.group()) },
    Regex("[*/]") to { m -> MathToken.Operator.MultOp(m.group()) },
    Regex("[-+]") to { m -> MathToken.Operator.AddOp(m.group()) },
    Regex("\\(") to { _ -> MathToken.OpenParen },
    Regex("\\)") to { _ -> MathToken.CloseParen }
)

class TokenizerTest : FunSpec({
    test("math") {

        MATH_TOKENIZER.tokenize(CharOffsetTracker, "foo bar 123 baz789 45.6 45 .6 hello").shouldContain(
                Positioned(0, MathToken.Value.Identifier("foo"), 3),
                Positioned(3, MathToken.Space(" "), 4),
                Positioned(4, MathToken.Value.Identifier("bar"), 7),
                Positioned(7, MathToken.Space(" "), 8),
                Positioned(8, MathToken.Value.IntLiteral(123), 11),
                Positioned(11, MathToken.Space(" "), 12),
                Positioned(12, MathToken.Value.Identifier("baz789"), 18),
                Positioned(18, MathToken.Space(" "), 19),
                Positioned(19, MathToken.Value.FloatLiteral(45.6), 23),
                Positioned(23, MathToken.Space(" "), 24),
                Positioned(24, MathToken.Value.IntLiteral(45), 26),
                Positioned(26, MathToken.Space(" "), 27),
                Positioned(27, MathToken.Value.FloatLiteral(.6), 29),
                Positioned(29, MathToken.Space(" "), 30),
                Positioned(30, MathToken.Value.Identifier("hello"), 35)
        )
    }

    test("same length") {
        val tokenizer = RegexTokenizer<String>(
            Regex("reserved") to { _ -> "reserved" },
            Regex("\\p{Alpha}[\\p{Alpha}0-9]+") to { _ -> "identifier" },
            Regex("\\s+") to { _ -> "space" }
        )

        tokenizer.tokenize("foo bar baz").shouldContain(
            "identifier", "space", "identifier", "space", "identifier"
        )

        tokenizer.tokenize("foo reserved baz").shouldContain(
            "identifier", "space", "reserved", "space", "identifier"
        )

        tokenizer.tokenize("foo reserve baz").shouldContain(
            "identifier", "space", "identifier", "space", "identifier"
        )

        tokenizer.tokenize("foo reservednot baz").shouldContain(
            "identifier", "space", "identifier", "space", "identifier"
        )

        tokenizer.tokenize("foo notreservednot baz").shouldContain(
            "identifier", "space", "identifier", "space", "identifier"
        )

        tokenizer.tokenize("foo notreserved baz").shouldContain(
            "identifier", "space", "identifier", "space", "identifier"
        )
    }
})
