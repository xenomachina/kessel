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
import com.xenomachina.stream.asStream
import com.xenomachina.stream.streamOf
import kotlin.coroutines.experimental.buildIterator

// TODO: add metadata here
data class ParseError(val message: () -> String)

typealias ParseResult<R> = Either<ParseError, Stream.NonEmpty<R>>

fun <A, B> ParseResult<A>.map2(transform: (A) -> B) : ParseResult<B> =
    map { stream -> stream.map { a -> transform(a) } }

fun <T, A, B> ParseResult<PartialParse<T, A>>.map3(transform: (A) -> B) : ParseResult<PartialParse<T, B>> =
    map2 { it.map(transform) }

data class PartialParse<T, out R> (
        val parse: R,
        val remaining: Stream<T>
) : Functor<R> {
    override fun <F> map(f: (R) -> F) = PartialParse(f(parse), remaining)
}

interface Parser<T, out R> {
    //fun parse(stream: Stream<T>): ParseResult<T, R>
    fun partialParse(stream: Stream<T>): ParseResult<PartialParse<T, R>>
}

fun <T, A, B> Parser<T, A>.map(transform: (A) -> B) : Parser<T, B> = let { original ->
    object : Parser<T, B> {
        override fun partialParse(stream: Stream<T>): ParseResult<PartialParse<T, B>> =
            original.partialParse(stream).map3(transform)
    }
}

fun <T> terminal(predicate: (T) -> Boolean) = object : Parser<T, T> {
    override fun partialParse(stream: Stream<T>): ParseResult<PartialParse<T, T>> =
        when (stream) {
            is Stream.Empty ->
                Either.Left(ParseError { "Unexpected end of input" })

            is Stream.NonEmpty ->
                if (predicate(stream.head)) {
                    Either.Right(streamOf(PartialParse(stream.head, stream.tail)))
                } else {
                    Either.Left(ParseError { "Unexpected: ${stream.head}" })
                }
        }
}

fun <T, R> oneOf(parser1: Parser<T, R>, vararg parsers: () -> Parser<T, R>) : Parser<T, R> =
    object : Parser<T, R> {
        override fun partialParse(stream: Stream<T>): ParseResult<PartialParse<T, R>> {
            var parse = parser1.partialParse(stream)
            loop@ for (parser in parsers) {
                parse = when (parse) {
                    is Either.Left -> parser().partialParse(stream)
                    is Either.Right -> break@loop
                }
            }
            // TODO: construct "expected one of" error if parse fails
            return parse
        }
    }

fun <T, A, B, Z> sequence(
        parserA: Parser<T, A>,
        parserB: Parser<T, B>,
        f: (A, B) -> Z
) : Parser<T, Z> =
        object : Parser<T, Z> {
            override fun partialParse(stream: Stream<T>): ParseResult<PartialParse<T, Z>> {
                var parseError : ParseError? = null
                val partials = buildIterator {
                    parseError = iterateParser(parserA, stream) { a, stream ->
                        parseError = iterateParser(parserB, stream) { b, stream ->
                            yield(PartialParse(f(a, b), stream))
                        }
                    }
                }.asStream()
                return when (partials) {
                    is Stream.Empty -> Either.Left(parseError!!)
                    is Stream.NonEmpty-> Either.Right(partials)
                }
            }
        }

fun <T, A, B, C, Z> sequence(
        parserA: Parser<T, A>,
        parserB: Parser<T, B>,
        parserC: Parser<T, C>,
        f: (A, B, C) -> Z
) : Parser<T, Z> =
        object : Parser<T, Z> {
            override fun partialParse(stream: Stream<T>): ParseResult<PartialParse<T, Z>> {
                var parseError : ParseError? = null
                val partials = buildIterator {
                    parseError = iterateParser(parserA, stream) { a, stream ->
                        parseError = iterateParser(parserB, stream) { b, stream ->
                            parseError = iterateParser(parserC, stream) { c, stream ->
                                yield(PartialParse(f(a, b, c), stream))
                            }
                        }
                    }
                }.asStream()
                return when (partials) {
                    is Stream.Empty -> Either.Left(parseError!!)
                    is Stream.NonEmpty-> Either.Right(partials)
                }
            }
        }
fun <T, A, B, C, D, Z> sequence(
        parserA: Parser<T, A>,
        parserB: Parser<T, B>,
        parserC: Parser<T, C>,
        parserD: Parser<T, D>,
        f: (A, B, C, D) -> Z
) : Parser<T, Z> =
        object : Parser<T, Z> {
            override fun partialParse(stream: Stream<T>): ParseResult<PartialParse<T, Z>> {
                var parseError : ParseError? = null
                val partials = buildIterator {
                    parseError = iterateParser(parserA, stream) { a, stream ->
                        parseError = iterateParser(parserB, stream) { b, stream ->
                            parseError = iterateParser(parserC, stream) { c, stream ->
                                parseError = iterateParser(parserD, stream) { d, stream ->
                                    yield(PartialParse(f(a, b, c, d), stream))
                                }
                            }
                        }
                    }
                }.asStream()
                return when (partials) {
                    is Stream.Empty -> Either.Left(parseError!!)
                    is Stream.NonEmpty-> Either.Right(partials)
                }
            }
        }

fun <T, A, B, C, D, E, Z> sequence(
        parserA: Parser<T, A>,
        parserB: Parser<T, B>,
        parserC: Parser<T, C>,
        parserD: Parser<T, D>,
        parserE: Parser<T, E>,
        f: (A, B, C, D, E) -> Z
) : Parser<T, Z> =
    object : Parser<T, Z> {
        override fun partialParse(stream: Stream<T>): ParseResult<PartialParse<T, Z>> {
            var parseError : ParseError? = null
            val partials = buildIterator {
                parseError = iterateParser(parserA, stream) { a, stream ->
                    parseError = iterateParser(parserB, stream) { b, stream ->
                        parseError = iterateParser(parserC, stream) { c, stream ->
                            parseError = iterateParser(parserD, stream) { d, stream ->
                                parseError = iterateParser(parserE, stream) { e, stream ->
                                    yield(PartialParse(f(a, b, c, d, e), stream))
                                }
                            }
                        }
                    }
                }
            }.asStream()
            return when (partials) {
                is Stream.Empty -> Either.Left(parseError!!)
                is Stream.NonEmpty-> Either.Right(partials)
            }
        }
    }

private inline fun <T, A> iterateParser(parser: Parser<T, A>, stream: Stream<T>, body: (A, Stream<T>) -> Unit) : ParseError? {
    val firstParse = parser.partialParse(stream)
    when (firstParse) {
        is Either.Left -> return firstParse.left
        is Either.Right -> {
            for (partial in firstParse.right) {
                body(partial.parse, partial.remaining)
            }
            return null
        }
    }
}
