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
import com.xenomachina.common.Functor
import kotlin.coroutines.experimental.buildSequence

// TODO: add metadata here
data class ParseError(val message: () -> String)

typealias ParseResult<R> = Either<ParseError, Stream<R>>

data class PartialParse<T, out R> (
        val parse: R,
        val remaining: Stream<T>?
) : Functor<R> {
    override fun <F> map(f: (R) -> F) = PartialParse(f(parse), remaining)
}

interface Parser<T, out R> {
    //fun parse(stream: Stream<T>?): ParseResult<T, R>
    fun partialParse(stream: Stream<T>?): ParseResult<PartialParse<T, R>>
}

fun <T> terminal(predicate: (T) -> Boolean) = object : Parser<T, T> {
    override fun partialParse(stream: Stream<T>?): ParseResult<PartialParse<T, T>> {
        if (stream == null) {
            return Either.Left(ParseError { "Unexpected end of input" })
        } else {
            if (predicate(stream.head)) {
                return Either.Right(streamOf(PartialParse(stream.head, stream.tail)))
            } else {
                return Either.Left(ParseError { "Unexpected: ${stream.head}" })
            }
        }
    }
}

fun <T, A, B> Parser<T, A>.or(that: () -> Parser<T, B>) : Parser<T, Unit> = this.or(that, {Unit}, {Unit})

fun <T, A, B, R> Parser<T, A>.or(that: () -> Parser<T, B>, transformA: (A) -> R, transformB:(B) -> R) = let { thisParser ->
    object : Parser<T, R> {
        override fun partialParse(stream: Stream<T>?) = thisParser.partialParse(stream).let { firstParse ->
            when (firstParse) {
                is Either.Left -> that().partialParse(stream).map { stream -> stream.map { partial -> partial.map(transformB) } }
                else -> firstParse.map { stream -> stream.map { partial -> partial.map(transformA) } }
            }
        }
    }
}

fun <T, A, B> Parser<T, A>.then(secondParser: () -> Parser<T, B>) : Parser<T, Unit> = then(secondParser, {_, _ -> Unit})

fun <T, A, B, R> Parser<T, A>.then(
        secondParser: () -> Parser<T, B>,
        transform: (A, B) -> R
) : Parser<T, R> = let { firstParser ->
    object : Parser<T, R> {
        override fun partialParse(stream: Stream<T>?) = firstParser.partialParse(stream).let { firstParse ->
            when (firstParse) {
                is Either.Left -> firstParse // just return that error
                is Either.Right -> {
                    val resultStream = buildSequence {
                        for (partial in firstParse.value) {
                            val first = partial.parse
                            val remaining = partial.remaining
                            val secondParse = secondParser().partialParse(remaining)
                            when (secondParse) {
                                is Either.Left -> TODO()
                                is Either.Right -> for (secondPartial in secondParse.value) {
                                    yield(PartialParse(transform(first, secondPartial.parse), secondPartial.remaining))
                                }
                            }
                        }
                    }.asStream()
                    if (resultStream == null) {
                        TODO()
                    } else {
                        Either.Right(resultStream)
                    }
                }
            }
        }
    }
}
