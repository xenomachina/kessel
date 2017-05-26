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
import com.xenomachina.stream.Stream
import com.xenomachina.stream.buildStream
import com.xenomachina.stream.plus
import com.xenomachina.stream.streamOf
import kotlin.coroutines.experimental.SequenceBuilder

// TODO: add metadata here
// TODO: remove `consumed`?
data class ParseError(val consumed: Int, val message: () -> String)

data class PartialResult<T, out R>(
        val consumed: Int,
        val value: Either<ParseError, R>,
        val remaining: Stream<T>
) : Functor<R> {
    override fun <F> map(f: (R) -> F) = PartialResult(consumed, value.map(f), remaining)
}

interface Parser<T, out R> {
    fun partialParse(consumed: Int, stream: Stream<T>): Stream.NonEmpty<PartialResult<T, R>>
}

fun <T, A, B> Parser<T, A>.map(transform: (A) -> B) : Parser<T, B> = let { original ->
    object : Parser<T, B> {
        override fun partialParse(
                consumed: Int,
                stream: Stream<T>
        ): Stream.NonEmpty<PartialResult<T, B>> =
            original.partialParse(consumed, stream).map { it.map(transform) }
    }
}

// TODO: add epsilon
// TODO: add endOfInput

fun <T> terminal(predicate: (T) -> Boolean) = object : Parser<T, T> {
    override fun partialParse(consumed: Int, stream: Stream<T>): Stream.NonEmpty<PartialResult<T, T>> =
        when (stream) {
            is Stream.Empty ->
                streamOf(PartialResult<T, T>(consumed, Either.Left(ParseError(0) { "Unexpected end of input" }), stream))

            is Stream.NonEmpty ->
                if (predicate(stream.head)) {
                    streamOf(PartialResult(consumed + 1, Either.Right(stream.head), stream.tail))
                } else {
                    streamOf(PartialResult(
                            consumed, Either.Left(ParseError(0) { "Unexpected: ${stream.head}" }), stream))
                }
        }
}

fun <T, R> oneOf(parser1: Parser<T, R>, vararg parsers: () -> Parser<T, R>) : Parser<T, R> =
    object : Parser<T, R> {
        override fun partialParse(consumed: Int, stream: Stream<T>): Stream.NonEmpty<PartialResult<T, R>> {
            var result : Stream.NonEmpty<PartialResult<T, R>> = parser1.partialParse(consumed, stream)
            for (thunk in parsers) {
                result = result + { thunk().partialParse(consumed, stream) }
            }
            return result
        }
    }

fun <T, A, B, Z> seq(
        parserA: Parser<T, A>,
        parserB: Parser<T, B>,
        f: (A, B) -> Z
) : Parser<T, Z> =
        object : Parser<T, Z> {
            override fun partialParse(consumed: Int, stream: Stream<T>): Stream.NonEmpty<PartialResult<T, Z>> =
                    // TODO: remove type params when Kotlin compiler can infer without crashing
                    buildStream<PartialResult<T, Z>> {
                        forParser(parserA, consumed, stream) { consumed, a, remaining ->
                            forParser(parserB, consumed, remaining) { consumed, b, remaining ->
                                yield(PartialResult(consumed, Either.Right(f(a, b)), remaining))
                            }
                        }
                    } as Stream.NonEmpty<PartialResult<T, Z>>
        }

fun <T, A, B, C, D, E, Z> seq(
        parserA: Parser<T, A>,
        parserB: Parser<T, B>,
        parserC: Parser<T, C>,
        parserD: Parser<T, D>,
        parserE: Parser<T, E>,
        f: (A, B, C, D, E) -> Z
) : Parser<T, Z> =
        object : Parser<T, Z> {
            override fun partialParse(consumed: Int, stream: Stream<T>): Stream.NonEmpty<PartialResult<T, Z>> =
                    // TODO: remove type params when Kotlin compiler can infer without crashing
                    buildStream<PartialResult<T, Z>> {
                        forParser(parserA, consumed, stream) { consumed, a, remaining ->
                            forParser(parserB, consumed, remaining) { consumed, b, remaining ->
                                forParser(parserC, consumed, remaining) { consumed, c, remaining ->
                                    forParser(parserD, consumed, remaining) { consumed, d, remaining ->
                                        forParser(parserE, consumed, remaining) { consumed, e, remaining ->
                                            yield(PartialResult(consumed, Either.Right(f(a, b, c, d, e)), remaining))
                                        }
                                    }
                                }
                            }
                        }
                    } as Stream.NonEmpty<PartialResult<T, Z>>
        }

private inline suspend fun <T, R, Z> SequenceBuilder<PartialResult<T, Z>>.forParser(
        parser: Parser<T, R>,
        consumed: Int,
        remaining: Stream<T>,
        body: (consumed: Int, value: R, remaining: Stream<T>) -> Unit
) {
    for (partialResult in parser.partialParse(consumed, remaining)) {
        when (partialResult.value) {
            is Either.Left ->
                // TODO: use unchecked cast? (object should be identical)
                yield(PartialResult(partialResult.consumed, partialResult.value, partialResult.remaining))
            is Either.Right -> body(consumed, partialResult.value.right, remaining)
        }
    }
}
