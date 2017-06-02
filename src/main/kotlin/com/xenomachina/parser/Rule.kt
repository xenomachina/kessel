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
import com.xenomachina.common.Maybe
import com.xenomachina.stream.Stream
import com.xenomachina.stream.buildStream
import com.xenomachina.stream.plus
import com.xenomachina.stream.streamOf
import java.util.IdentityHashMap

abstract class Rule<in T, out R> {
    abstract internal fun <Q : T> partialParse(
            consumed: Int,
            // TODO: change breadcrumbs to use a Stream instead of Map?
            breadcrumbs: Map<Rule<*, *>, Int>,
            stream: Stream<Q>
    ): Stream.NonEmpty<PartialResult<Q, R>>

    internal fun <Q : T> call(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            stream: Stream<Q>
    ): Stream.NonEmpty<PartialResult<Q, R>> {
        if (breadcrumbs.get(this) == consumed) {
            throw IllegalStateException("Left recursion detected")
        } else {
            return partialParse(consumed, IdentityHashMap(breadcrumbs).apply { put(this@Rule, consumed) }, stream)
        }
    }
}

fun <T, A, B> Rule<T, A>.map(transform: (A) -> B) : Rule<T, B> = let { original ->
    object : Rule<T, B>() {
        override fun <Q : T> partialParse(
                consumed: Int,
                breadcrumbs: Map<Rule<*, *>, Int>,
                stream: Stream<Q>
        ): Stream.NonEmpty<PartialResult<Q, B>> =
            original.call(consumed, breadcrumbs, stream).map { it.map(transform) }
    }
}

class Epsilon<T> : Rule<T, Unit>() {
    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            stream: Stream<Q>
    ): Stream.NonEmpty<PartialResult<Q, Unit>> =
            streamOf(PartialResult( consumed, Either.Right(Unit), stream))
}

val END_OF_INPUT = object : Rule<Any?, Unit>() {
    override fun <Q> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            stream: Stream<Q>
    ): Stream.NonEmpty<PartialResult<Q, Unit>> =
            when (stream) {
                is Stream.Empty ->
                    streamOf<PartialResult<Q, Unit>>(PartialResult(consumed, Either.Right(Unit), stream))

                is Stream.NonEmpty ->
                    streamOf(PartialResult(
                            consumed,
                            Either.Left(ParseError(consumed, stream.maybeHead) { "Expected end of input, found: ${stream.head}" }),
                            stream))
            }
}

class Terminal<T>(val predicate: (T) -> Boolean) : Rule<T, T>() {
    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            stream: Stream<Q>
    ): Stream.NonEmpty<PartialResult<Q, T>> =
            when (stream) {
                is Stream.Empty ->
                    streamOf(PartialResult<Q, T>(
                            consumed,
                            Either.Left(ParseError(consumed, stream.maybeHead) { "Unexpected end of input" }),
                            stream))

                is Stream.NonEmpty ->
                    if (predicate(stream.head)) {
                        streamOf(PartialResult(
                                consumed + 1,
                                Either.Right(stream.head),
                                stream.tail))
                    } else {
                        streamOf(PartialResult(
                                consumed,
                                Either.Left(ParseError(consumed, stream.maybeHead) { "Unexpected: ${stream.head}" }),
                                stream))
                    }
            }
}

/**
 * A lazy wrapper around another Parser. This is useful for creating recursive parsers. For example:
 *
 *     val listOfWidgets = oneOf(epsilon(), seq(widget, L(listOfWidgets)))
 */
class LazyRule<T, R>(inner: () -> Rule<T, R>) : Rule<T, R>() {
    val inner by lazy(inner)
    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            stream: Stream<Q>
    ): Stream.NonEmpty<PartialResult<Q, R>> =
            this.inner.call(consumed, breadcrumbs, stream)
}

class AlternativeRule<T, R>(private val rule1: Rule<T, R>, vararg rules: Rule<T, R>) : Rule<T, R>() {
    private val rules = listOf(*rules)

    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            stream: Stream<Q>
    ): Stream.NonEmpty<PartialResult<Q, R>> {
        var result : Stream.NonEmpty<PartialResult<Q, R>> = rule1.call(consumed, breadcrumbs, stream)
        for (parser in rules) {
            result = result + { parser.call(consumed, breadcrumbs, stream) }
        }
        return result
    }
}

// TODO: once KT-18268 is fixed, change this to use a "forRule" helper method
class Sequence2Rule<T, A, B, Z> (
        val ruleA: Rule<T, A>,
        val ruleB: Rule<T, B>,
        val constructor: (A, B) -> Z
) : Rule<T, Z>() {
    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            stream: Stream<Q>
    ): Stream.NonEmpty<PartialResult<Q, Z>> =
            // TODO: remove type params when Kotlin compiler can infer without crashing
            buildStream<PartialResult<Q, Z>> {
                for (partialA in ruleA.call(consumed, breadcrumbs, stream)) {
                    when (partialA.value) {
                        is Either.Left ->
                            // TODO: use unchecked cast? (object should be identical)
                            yield(PartialResult(partialA.consumed, partialA.value, partialA.remaining))
                        is Either.Right -> // body(consumed, partialResult.value.right, remaining)
                            for (partialB in ruleB.call(partialA.consumed, breadcrumbs, partialA.remaining)) {
                                when (partialB.value) {
                                    is Either.Left ->
                                        // TODO: use unchecked cast? (object should be identical)
                                        yield(PartialResult(partialB.consumed, partialB.value,
                                                partialB.remaining))
                                    is Either.Right -> //body(consumed, partialResult.value.right, remaining)
                                        yield(PartialResult(consumed,
                                                Either.Right(constructor(partialA.value.right, partialB.value.right)),
                                                partialB.remaining))
                                }
                            }
                    }
                }
            } as Stream.NonEmpty<PartialResult<Q, Z>>
}

/**
 * @property consumed how many input tokens were sucessfully consumed to construct the sucessful result or before
 * failing
 * @property value either the parsed value, or a `ParseError` in the case of failure
 * @property remaining the remaining stream after the parsed value, or at the point of failure
 */
internal data class PartialResult<out T, out R>(
        val consumed: Int,
        val value: Either<ParseError<T>, R>,
        val remaining: Stream<T>
) : Functor<R> {
    override fun <F> map(f: (R) -> F) = PartialResult(consumed, value.map(f), remaining)
}

val <T> Stream<T>.maybeHead
    get() = when (this) {
        is Stream.NonEmpty -> Maybe.Just(head)
        is Stream.Empty -> Maybe.NOTHING
    }
