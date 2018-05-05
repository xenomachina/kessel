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

import com.xenomachina.chain.Chain
import com.xenomachina.chain.asChain
import com.xenomachina.common.Either
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.FunSpec

private fun tokenChain(s: String) = MATH_TOKENIZER.tokenize(CharOffsetTracker, s)
        .map { it.value }
        .filter { !(it is MathToken.Space) }.asChain()

sealed class Expr {
    data class Op(val left: Expr, val op: MathToken, val right: Expr) : Expr()
    data class Leaf(val value: MathToken) : Expr()
}

fun <L, R> Either<L, R>.assertRight(): R = (this as Either.Right<R>).right
fun <L, R> Either<L, R>.assertLeft(): L = (this as Either.Left<L>).left
fun <T> Chain<T>.assertHead(): T = (this as Chain.NonEmpty<T>).head

class ParserTest : FunSpec({
    test("simple") {
        val parser = Parser.Builder {
            seq(isA<MathToken.IntLiteral>(), END_OF_INPUT) { integer, _ -> integer.value.toInt() }
        }.build()

        parser.parse(tokenChain("5")) shouldEqual Either.Right(5)

        parser.parse(tokenChain("hello")).assertLeft().first().message
                .shouldEqual("Unexpected: Identifier(name=hello)")
    }

    // TODO: add test where nullable is true

    test("expression") {

        lateinit var multRule: Rule<*, *>
        lateinit var exprRule: Rule<*, *>

        val parser = Parser.Builder {
            val grammar = object {
                val multOp = isA<MathToken.MultOp>()

                val addOp = isA<MathToken.AddOp>()

                val factor = oneOf(
                            isA<MathToken.IntLiteral>().map(Expr::Leaf),
                            isA<MathToken.Identifier>().map(Expr::Leaf),
                            seq(
                                    isA<MathToken.OpenParen>(),
                                    recur { expression },
                                    isA<MathToken.CloseParen>()
                            ) { _, expr, _ -> expr }
                    )

                val term: Rule<MathToken, Expr> by lazy {
                    oneOf(
                            factor,
                            seq(factor, multOp, recur { term }) { l, op, r -> Expr.Op(l, op, r) }
                    )
                }

                val expression: Rule<MathToken, Expr> by lazy {
                    oneOf(
                            term,
                            seq(term, addOp, recur { expression }) { l, op, r -> Expr.Op(l, op, r) }
                    )
                }
            }

            multRule = grammar.multOp
            exprRule = grammar.expression

            seq(grammar.expression, END_OF_INPUT) { expr, _ -> expr }
        }.build()

        parser.ruleProps[multRule]!!.nullable shouldEqual false
        parser.ruleProps[exprRule]!!.nullable shouldEqual false

        val ast = parser.parse(tokenChain("5 * (3 + 7) - (4 / (2 - 1))")).assertRight()
        ast as Expr.Op
        ast.op as MathToken.AddOp
        ast.op.name shouldEqual "-"

        // 5 * (3 + 7)
        ast.left as Expr.Op
        ast.left.op as MathToken.MultOp
        ast.left.op.name shouldEqual "*"

        // 5
        ast.left.left as Expr.Leaf
        ast.left.left.value as MathToken.IntLiteral
        ast.left.left.value.value shouldEqual 5

        // (3 + 7)
        ast.left.right as Expr.Op
        ast.left.right.op as MathToken.AddOp
        ast.left.right.op.name shouldEqual "+"
    }

    test("simple left recursion") {
        val parser = Parser.Builder {
            val grammar = object {
                val addOp = isA<MathToken.AddOp>()
                val number = isA<MathToken.IntLiteral>().map(Expr::Leaf)

                val expression: Rule<MathToken, Expr> by lazy { oneOf<MathToken, Expr>(
                        number,
                        seq(recur { expression }, addOp, number) { l, op, r -> Expr.Op(l, op, r) }
                ) }
            }
            seq(grammar.expression, END_OF_INPUT) { expr, _ -> expr }
        }.build()

        shouldThrow<IllegalStateException> {
            parser.parse(tokenChain("1 + 2 + 3 + 4"))
        }.run {
            // Left-recursion is not currently supported.
            message shouldEqual "Left recursion detected"
        }
    }

    // TODO: support left recursion, and re-enable this test
//    test("left_recursion") {
//        val grammar = object {
//            val multOp = isA<MathToken.MultOp>()
//
//            val addOp = isA<MathToken.AddOp>()
//
//            val factor : Parser<MathToken, Expr> by lazy { oneOf<MathToken, Expr>(
//                    isA<MathToken.IntLiteral>().map(Expr::Leaf),
//                    isA<MathToken.Identifier>().map(Expr::Leaf),
//                    seq(
//                            isA<MathToken.OpenParen>(),
//                            L { expression },
//                            isA<MathToken.CloseParen>()
//                    ) { _, expr, _ -> expr }
//            ) }
//
//            val term : Parser<MathToken, Expr> by lazy { oneOf<MathToken, Expr>(
//                    factor,
//                    seq(L { term }, multOp, L { factor }) { l, op, r -> Expr.Op(l, op, r) }
//            ) }
//
//            val expression : Parser<MathToken, Expr> by lazy { oneOf<MathToken, Expr>(
//                    term,
//                    seq(L { expression }, addOp, L { term }) { l, op, r -> Expr.Op(l, op, r) }
//            ) }
//        }
//        val parser = seq(grammar.expression, endOfInput()) { expr, _ -> expr }
//        val ast = parser.parse(tokenChain("5 * (3 + 7) - (4 / (2 - 1))"))
//        ast.assertRight().javaClass shouldEqual Expr.Op::class
//    }
})
