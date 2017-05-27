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

import com.xenomachina.common.Either
import com.xenomachina.stream.asStream
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.FunSpec

private fun tokenStream(s: String) = TEST_TOKENIZER.tokenize(s)
        .map { it.value }
        .filter { !(it is TestToken.Space) }.asStream()

class ParserTest : FunSpec({
    test("simple") {
        val parser = seq(isA(TestToken.Integer::class), endOfInput()) { integer, _ -> integer.value.toInt() }

        parser.parse(tokenStream("5")) shouldEqual Either.Right(5)

        parser.parse(tokenStream("hello")) shouldEqual
                Either.Left(listOf(ParseError { "Unexpected: Identifier(value=hello)" }))
    }

    test("expression") {
        val stream = tokenStream("""
    5 * (3 + 7) - (4 / (2 - 1))
            """)

    }
})
