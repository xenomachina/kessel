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
import java.lang.Thread.yield
import kotlin.coroutines.experimental.buildSequence

// TODO: add metadata here
data class ParseError(val message: () -> String)

typealias ParseResult<R> = Either<ParseError, Stream<R>>

data class PartialParse<T, out R>(
        val parse: R,
        val remaining: Stream<T>?
)

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

fun <T, R> Parser<T, R>.or(otherParser: () -> Parser<T, R>) = let { thisParser ->
    object : Parser<T, R> {
        override fun partialParse(stream: Stream<T>?) = thisParser.partialParse(stream).let { firstParse ->
            when (firstParse) {
                is Either.Left -> otherParser().partialParse(stream) // TODO: combine errors
                else -> firstParse
            }
        }
    }
}

fun <T, A, B> Parser<T, A>.then(otherParser: () -> Parser<T, B>) = let { thisParser ->
    object : Parser<T, Pair<A, B>> {
        override fun partialParse(stream: Stream<T>?) = thisParser.partialParse(stream).let { firstParse ->
            when (firstParse) {
                is Either.Left -> firstParse // just return that error
                is Either.Right -> {
                    val resultStream = buildSequence {
                        for (partial in firstParse.value) {
                            val first = partial.parse
                            val remaining = partial.remaining
                            val secondParse = otherParser().partialParse(remaining)
                            when (secondParse) {
                                is Either.Left -> TODO()
                                is Either.Right -> for (secondPartial in secondParse.value) {
                                    yield(PartialParse(Pair(first, secondPartial.parse), secondPartial.remaining))
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
