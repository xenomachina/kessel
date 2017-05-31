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

sealed class Expr {
    data class Op(val left: Expr, val op: TestToken, val right: Expr) : Expr()
    data class Leaf(val value: TestToken) : Expr()
}

fun <L, R> Either<L, R>.getRight() : R {
    when (this) {
        is Either.Left -> throw AssertionError("Expected Right, got $this")
        is Either.Right -> return this.right
    }
}

class ParserTest : FunSpec({
    test("simple") {
        val parser = seq(isA(TestToken.Integer::class), endOfInput()) { integer, _ -> integer.value.toInt() }

        parser.parse(tokenStream("5")) shouldEqual Either.Right(5)

        parser.parse(tokenStream("hello")) shouldEqual
                Either.Left(listOf(ParseError { "Unexpected: Identifier(value=hello)" }))
    }

    test("expression") {
        val parseTree = object {
            val multOp = isA(TestToken.MultOp::class)

            val addOp = isA(TestToken.AddOp::class)

            val factor : Parser<TestToken, Expr> by lazy { oneOf<TestToken, Expr>(
                    isA(TestToken.Integer::class).map(Expr::Leaf),
                    isA(TestToken.Identifier::class).map(Expr::Leaf),
                    seq(
                            isA(TestToken.OpenParen::class),
                            L { expression },
                            isA(TestToken.CloseParen::class)
                    ) { _, expr, _ -> expr }
            ) }

            val term : Parser<TestToken, Expr> by lazy { oneOf<TestToken, Expr>(
                    factor,
                    seq(L { term }, multOp, L { factor }) { l, op, r -> Expr.Op(l, op, r) }
            ) }

            val expression : Parser<TestToken, Expr> by lazy { oneOf<TestToken, Expr>(
                    term,
                    seq(L { expression }, addOp, L { term }) { l, op, r -> Expr.Op(l, op, r) }
            ) }
        }
        val parser = seq(parseTree.expression, endOfInput()) { expr, _ -> expr }
        val ast = parser.parse(tokenStream("5 * (3 + 7) - (4 / (2 - 1))"))
        // TODO: ast.getRight().javaClass shouldEqual Expr.Op::class
    }
})
