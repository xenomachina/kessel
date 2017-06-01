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
import kotlin.reflect.KClass

/**
 * @property message error message
 */
class ParseError<out T>(val consumed: Int, val element: Maybe<T>, message: () -> String) {
    val message by lazy { message() }

    override fun toString(): String {
        return "ParseError(\"$message\" @ $consumed :: <$element>)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as ParseError<*>

        if (consumed != other.consumed) return false
        if (element != other.element) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = consumed
        result = 31 * result + element.hashCode()
        result = 47 * result + message.hashCode()
        return result
    }
}

// TODO: make PartialResult private
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

class Parser<in T, out R>(private val start: Rule<T, R>) {
    fun <Q : T> parse(stream: Stream<Q>): Either<List<ParseError<Q>>, R> {
        val breadcrumbs = IdentityHashMap<Rule<*, *>, Int>()
        val errors = mutableListOf<ParseError<Q>>()
        var bestConsumed = 0
        for (partial in start.call(0, breadcrumbs, stream)) {
            when (partial.value) {
                is Either.Left -> {
                    if (partial.consumed > bestConsumed) {
                        bestConsumed = partial.consumed
                        errors.clear()
                    }
                    if (partial.consumed == bestConsumed) {
                        errors.add(partial.value.left)
                    }
                }
                is Either.Right -> return Either.Right(partial.value.right)
            }
        }
        return Either.Left(errors)
    }

    class Builder<in T, out R>(private val body: () -> Rule<T, R>) {
        fun build(): Parser<T, R> = Parser(body())
    }
}

abstract class Rule<in T, out R>(val typeName: String) {

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
    object : Rule<T, B>("map") {
        override fun <Q : T> partialParse(
                consumed: Int,
                breadcrumbs: Map<Rule<*, *>, Int>,
                stream: Stream<Q>
        ): Stream.NonEmpty<PartialResult<Q, B>> =
            original.call(consumed, breadcrumbs, stream).map { it.map(transform) }
    }
}

// TODO: make these into named classes
fun <T> epsilon() = object : Rule<T, Unit>("epsilon") {
    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            stream: Stream<Q>
    ): Stream.NonEmpty<PartialResult<Q, Unit>> =
            streamOf(PartialResult( consumed, Either.Right(Unit), stream))
}

fun <T> endOfInput() = object : Rule<T, Unit>("endOfInput") {
    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            stream: Stream<Q>
    ): Stream.NonEmpty<PartialResult<Q, Unit>> =
            when (stream) {
                is Stream.Empty ->
                    streamOf<PartialResult<Q, Unit>>(PartialResult(consumed, Either.Right(Unit), stream))

                is Stream.NonEmpty<T> ->
                    streamOf(PartialResult(
                            consumed,
                            Either.Left(ParseError(consumed, stream.maybeHead) { "Expected end of input, found: ${stream.head}" }),
                            stream))
            }
}

fun <T : Any> isA(kclass: KClass<T>) : Rule<Any, T> {
    val javaClass = kclass.java
    return terminal<Any> { javaClass.isInstance(it) }.map { javaClass.cast(it) }
}

fun <T> terminal(predicate: (T) -> Boolean) =
        object : Rule<T, T>("terminal") {
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
 * Creates a lazy wrapper around another Parser. This is useful for creating recursive parsers. For example:
 *
 *     val listOfWidgets = oneOf(epsilon(), seq(widget, L(listOfWidgets)))
 */
fun <T, R> L(inner: () -> Rule<T, R>) : Rule<T, R> =
        object : Rule<T, R>("LAZY") {
            val inner by lazy(inner)
            override fun <Q : T> partialParse(
                    consumed: Int,
                    breadcrumbs: Map<Rule<*, *>, Int>,
                    stream: Stream<Q>
            ): Stream.NonEmpty<PartialResult<Q, R>> =
                this.inner.call(consumed, breadcrumbs, stream)
        }

fun <T, R> oneOf(rule1: Rule<T, R>, vararg rules: Rule<T, R>) : Rule<T, R> =
    object : Rule<T, R>("oneOf") {
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

fun <T, A, B, Z> seq(
        ruleA: Rule<T, A>,
        ruleB: Rule<T, B>,
        f: (A, B) -> Z
) : Rule<T, Z> =
        object : Rule<T, Z>("seq") {
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
                                                    Either.Right(f(partialA.value.right, partialB.value.right)),
                                                    partialB.remaining))
                                        }
                                    }
                            }
                        }
                    } as Stream.NonEmpty<PartialResult<Q, Z>>
        }

// TODO: inline to remove Pair construction
fun <T, A, B, C, Z> seq(
        ruleA: Rule<T, A>,
        ruleB: Rule<T, B>,
        ruleC: Rule<T, C>,
        f: (A, B, C) -> Z
) : Rule<T, Z> =
        seq(ruleA, seq(ruleB, ruleC) { b, c -> Pair(b, c) }) { a, b_c -> f(a, b_c.first, b_c.second) }

// TODO: implementations below blow up with "java.lang.NoClassDefFoundError: kotlin/coroutines/Markers" at runtime.
// TODO: make test case and file bug.

//fun <T, A, B, Z> seq(
//        parserA: Parser<T, A>,
//        parserB: Parser<T, B>,
//        f: (A, B) -> Z
//) : Parser<T, Z> =
//        object : Parser<T, Z>() {
//            override fun <Q : T> partialParse(
//                consumed: Int, 
//                breadcrumbs: Map<Parser<*, *>, Int>,
//                stream: Stream<Q>
//            ): Stream.NonEmpty<PartialResult<Q, Z>> =
//                    // TODO: remove type params when Kotlin compiler can infer without crashing
//                    buildStream<PartialResult<Q, Z>> {
//                        forParser(parserA, consumed, stream) { consumed, a, remaining ->
//                            forParser(parserB, consumed, remaining) { consumed, b, remaining ->
//                                yield(PartialResult(consumed, Either.Right(f(a, b)), remaining))
//                            }
//                        }
//                    } as Stream.NonEmpty<PartialResult<Q, Z>>
//        }

//fun <T, A, B, C, D, E, Z> seq(
//        parserA: Parser<T, A>,
//        parserB: Parser<T, B>,
//        parserC: Parser<T, C>,
//        parserD: Parser<T, D>,
//        parserE: Parser<T, E>,
//        f: (A, B, C, D, E) -> Z
//) : Parser<T, Z> =
//        object : Parser<T, Z>() {
//            override fun <Q : T> partialParse(
//                consumed: Int,
//                breadcrumbs: Map<Parser<*, *>, Int>,
//                stream: Stream<Q>
//            ): Stream.NonEmpty<PartialResult<Q, Z>> =
//                    // TODO: remove type params when Kotlin compiler can infer without crashing
//                    buildStream<PartialResult<Q, Z>> {
//                        forParser(parserA, consumed, stream) { consumed, a, remaining ->
//                            forParser(parserB, consumed, remaining) { consumed, b, remaining ->
//                                forParser(parserC, consumed, remaining) { consumed, c, remaining ->
//                                    forParser(parserD, consumed, remaining) { consumed, d, remaining ->
//                                        forParser(parserE, consumed, remaining) { consumed, e, remaining ->
//                                            yield(PartialResult(consumed, Either.Right(f(a, b, c, d, e)), remaining))
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    } as Stream.NonEmpty<PartialResult<Q, Z>>
//        }
//
//// TODO: inline
//private inline suspend fun <T, R, Z> SequenceBuilder<PartialResult<T, Z>>.forParser(
//        parser: Parser<T, R>,
//        consumed: Int,
//        remaining: Stream<T>,
//        body: (consumed: Int, value: R, remaining: Stream<T>) -> Unit
//) {
//    for (partialResult in parser.call(consumed, remaining)) {
//        when (partialResult.value) {
//            is Either.Left ->
//                // TODO: use unchecked cast? (object should be identical)
//                yield(PartialResult(partialResult.consumed, partialResult.value, partialResult.remaining))
//            is Either.Right -> body(consumed, partialResult.value.right, remaining)
//        }
//    }
//}
