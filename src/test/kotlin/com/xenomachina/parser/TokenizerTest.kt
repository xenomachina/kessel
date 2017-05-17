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

enum class Type { IDENTIFIER, INTEGER, FLOAT, SPACE }

class TokenizerTest : FunSpec({
    data class Token<P>(val start: P, val type: String, val value: String)

    test("simple") {
        val tokenizer = Tokenizer<Int, Pair<Type, String>>(
                CharOffsetTracker(),
                Regex("\\p{Alpha}[\\p{Alpha}0-9]+") to { m -> Type.IDENTIFIER to m.group() },
                Regex("\\d+") to { m -> Type.INTEGER to m.group() },
                Regex("\\d*\\.\\d") to { m -> Type.FLOAT to m.group() },
                Regex("\\s+") to { m -> Type.SPACE to m.group() })
        tokenizer.tokenize("foo bar 123 baz789 45.6 45 .6 hello").shouldContain(
                Positioned(0, Pair(Type.IDENTIFIER, "foo"), 3),
                Positioned(3, Pair(Type.SPACE, " "), 4),
                Positioned(4, Pair(Type.IDENTIFIER, "bar"), 7),
                Positioned(7, Pair(Type.SPACE, " "), 8),
                Positioned(8, Pair(Type.INTEGER, "123"), 11),
                Positioned(11, Pair(Type.SPACE, " "), 12),
                Positioned(12, Pair(Type.IDENTIFIER, "baz789"), 18),
                Positioned(18, Pair(Type.SPACE, " "), 19),
                Positioned(19, Pair(Type.FLOAT, "45.6"), 23),
                Positioned(23, Pair(Type.SPACE, " "), 24),
                Positioned(24, Pair(Type.INTEGER, "45"), 26),
                Positioned(26, Pair(Type.SPACE, " "), 27),
                Positioned(27, Pair(Type.FLOAT, ".6"), 29),
                Positioned(29, Pair(Type.SPACE, " "), 30),
                Positioned(30, Pair(Type.IDENTIFIER, "hello"), 35)
        )
    }
})
