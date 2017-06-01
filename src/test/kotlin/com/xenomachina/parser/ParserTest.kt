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
import com.xenomachina.stream.Stream
import com.xenomachina.stream.asStream
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.FunSpec

private fun tokenStream(s: String) = TEST_TOKENIZER.tokenize(s)
        .map { it.value }
        .filter { !(it is TestToken.Space) }.asStream()

sealed class Expr {
    data class Op(val left: Expr, val op: TestToken, val right: Expr) : Expr()
    data class Leaf(val value: TestToken) : Expr()
}

fun <L, R> Either<L, R>.assertRight() : R = (this as Either.Right<R>).right
fun <L, R> Either<L, R>.assertLeft() : L = (this as Either.Left<L>).left
fun <T> Stream<T>.assertHead() : T = (this as Stream.NonEmpty<T>).head

class ParserTest : FunSpec({
    test("simple") {
        val parser = Parser.Builder {
            seq(isA(TestToken.Integer::class), endOfInput()) { integer, _ -> integer.value.toInt() }
        }.build()

        parser.parse(tokenStream("5")) shouldEqual Either.Right(5)

        parser.parse(tokenStream("hello")).assertLeft().first().message
                .shouldEqual("Unexpected: Identifier(value=hello)")
    }

    test("expression") {
        val parser = Parser.Builder {
            val grammar = object {
                val multOp = isA(TestToken.MultOp::class)

                val addOp = isA(TestToken.AddOp::class)

                val factor = oneOf(
                            isA(TestToken.Integer::class).map(Expr::Leaf),
                            isA(TestToken.Identifier::class).map(Expr::Leaf),
                            seq(
                                    isA(TestToken.OpenParen::class),
                                    L { expression },
                                    isA(TestToken.CloseParen::class)
                            ) { _, expr, _ -> expr }
                    )

                val term : Rule<TestToken, Expr> by lazy {
                    oneOf(
                            factor,
                            seq(factor, multOp, L { term }) { l, op, r -> Expr.Op(l, op, r) }
                    )
                }

                val expression : Rule<TestToken, Expr> by lazy {
                    oneOf(
                            term,
                            seq(term, addOp, L { expression }) { l, op, r -> Expr.Op(l, op, r) }
                    )
                }

            }

            seq(grammar.expression, endOfInput()) { expr, _ -> expr }
        }.build()

        val ast = parser.parse(tokenStream("5 * (3 + 7) - (4 / (2 - 1))")).assertRight()
        ast as Expr.Op
        ast.op as TestToken.AddOp
        ast.op.value shouldEqual "-"

        // 5 * (3 + 7)
        ast.left as Expr.Op
        ast.left.op as TestToken.MultOp
        ast.left.op.value shouldEqual "*"

        // 5
        ast.left.left as Expr.Leaf
        ast.left.left.value as TestToken.Integer
        ast.left.left.value.value shouldEqual "5"

        // (3 + 7)
        ast.left.right as Expr.Op
        ast.left.right.op as TestToken.AddOp
        ast.left.right.op.value shouldEqual "+"
    }

    test("simple left recursion") {
        val parser = Parser.Builder {
            val grammar = object {
                val addOp = isA(TestToken.AddOp::class)
                val number = isA(TestToken.Integer::class).map(Expr::Leaf)

                val expression : Rule<TestToken, Expr> by lazy { oneOf<TestToken, Expr>(
                        number,
                        seq(L { expression }, addOp, number) { l, op, r -> Expr.Op(l, op, r) }
                ) }
            }
            seq(grammar.expression, endOfInput()) { expr, _ -> expr }
        }.build()

        shouldThrow<IllegalStateException> {
            parser.parse(tokenStream("1 + 2 + 3 + 4"))
        }.run {
            // Left-recursion is not currently supported.
            message shouldEqual "Left recursion detected"
        }
    }

    // TODO: support left recursion, and re-enable this test
//    test("left_recursion") {
//        val grammar = object {
//            val multOp = isA(TestToken.MultOp::class)
//
//            val addOp = isA(TestToken.AddOp::class)
//
//            val factor : Parser<TestToken, Expr> by lazy { oneOf<TestToken, Expr>(
//                    isA(TestToken.Integer::class).map(Expr::Leaf),
//                    isA(TestToken.Identifier::class).map(Expr::Leaf),
//                    seq(
//                            isA(TestToken.OpenParen::class),
//                            L { expression },
//                            isA(TestToken.CloseParen::class)
//                    ) { _, expr, _ -> expr }
//            ) }
//
//            val term : Parser<TestToken, Expr> by lazy { oneOf<TestToken, Expr>(
//                    factor,
//                    seq(L { term }, multOp, L { factor }) { l, op, r -> Expr.Op(l, op, r) }
//            ) }
//
//            val expression : Parser<TestToken, Expr> by lazy { oneOf<TestToken, Expr>(
//                    term,
//                    seq(L { expression }, addOp, L { term }) { l, op, r -> Expr.Op(l, op, r) }
//            ) }
//        }
//        val parser = seq(grammar.expression, endOfInput()) { expr, _ -> expr }
//        val ast = parser.parse(tokenStream("5 * (3 + 7) - (4 / (2 - 1))"))
//        ast.assertRight().javaClass shouldEqual Expr.Op::class
//    }
})
