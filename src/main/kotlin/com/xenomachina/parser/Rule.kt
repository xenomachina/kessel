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
import com.xenomachina.chain.buildChain
import com.xenomachina.chain.chainOf
import com.xenomachina.chain.plus
import com.xenomachina.common.Either
import com.xenomachina.common.Functor
import com.xenomachina.common.Maybe
import java.util.IdentityHashMap

abstract class Rule<in T, out R> {
    abstract internal fun <Q : T> partialParse(
            consumed: Int,
            // TODO: change breadcrumbs to use a Chain instead of Map?
            breadcrumbs: Map<Rule<*, *>, Int>,
            chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, R>>

    internal fun <Q : T> call(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, R>> {
        if (breadcrumbs.get(this) == consumed) {
            throw IllegalStateException("Left recursion detected")
        } else {
            return partialParse(consumed, IdentityHashMap(breadcrumbs).apply { put(this@Rule, consumed) }, chain)
        }
    }

    internal abstract fun computeRuleProperties(
            result: MutableMap<Rule<*, *>, Properties>,
            seen: MutableMap<Rule<*, *>, Boolean>
    ): Properties?

    data class Properties(
            val nullable: Boolean
    )
}

private inline fun Rule<*, *>.computeRulePropertiesHelper(
        result: MutableMap<Rule<*, *>, Rule.Properties>,
        seen: MutableMap<Rule<*, *>, Boolean>,
        crossinline body: () -> Rule.Properties?): Rule.Properties? {
    val props : Rule.Properties?
    if (seen.containsKey(this)) {
        props = result.get(this)
    } else {
        seen.put(this, false)
        props = body() ?: Rule.Properties(nullable = false)
        result.put(this, props)
        seen.put(this, true)
    }
    return props
}

fun <T, A, B> Rule<T, A>.map(transform: (A) -> B) : Rule<T, B> = MappingRule<T, A, B>(this, transform)

class MappingRule<T, A, B>(val original: Rule<T, A>, val transform: (A) -> B) : Rule<T, B>() {
    override fun computeRuleProperties(
            result: MutableMap<Rule<*, *>, Properties>,
            seen: MutableMap<Rule<*, *>, Boolean>
    ) = computeRulePropertiesHelper(result, seen) { original.computeRuleProperties(result, seen) }

    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, B>> =
            original.call(consumed, breadcrumbs, chain).map { it.map(transform) }
}

class Epsilon<T> : Rule<T, Unit>() {
    override fun computeRuleProperties(
            result: MutableMap<Rule<*, *>, Properties>,
            seen: MutableMap<Rule<*, *>, Boolean>
    ) = Properties(nullable = true)

    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, Unit>> =
            chainOf(PartialResult( consumed, Either.Right(Unit), chain))
}

val END_OF_INPUT = object : Rule<Any?, Unit>() {
    override fun computeRuleProperties(
            result: MutableMap<Rule<*, *>, Properties>,
            seen: MutableMap<Rule<*, *>, Boolean>
    ) = Properties(nullable = false)

    override fun <Q> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, Unit>> =
            when (chain) {
                is Chain.Empty ->
                    chainOf<PartialResult<Q, Unit>>(PartialResult(consumed, Either.Right(Unit), chain))

                is Chain.NonEmpty ->
                    chainOf(PartialResult(
                            consumed,
                            Either.Left(ParseError(consumed, chain.maybeHead) { "Expected end of input, found: ${chain.head}" }),
                            chain))
            }
}

class Terminal<T>(val predicate: (T) -> Boolean) : Rule<T, T>() {
    override fun computeRuleProperties(
            result: MutableMap<Rule<*, *>, Properties>,
            seen: MutableMap<Rule<*, *>, Boolean>
    ) = Properties(nullable = false)

    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, T>> =
            when (chain) {
                is Chain.Empty ->
                    chainOf(PartialResult<Q, T>(
                            consumed,
                            Either.Left(ParseError(consumed, chain.maybeHead) { "Unexpected end of input" }),
                            chain))

                is Chain.NonEmpty ->
                    if (predicate(chain.head)) {
                        chainOf(PartialResult(
                                consumed + 1,
                                Either.Right(chain.head),
                                chain.tail))
                    } else {
                        chainOf(PartialResult(
                                consumed,
                                Either.Left(ParseError(consumed, chain.maybeHead) { "Unexpected: ${chain.head}" }),
                                chain))
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

    override fun computeRuleProperties(
            result: MutableMap<Rule<*, *>, Properties>,
            seen: MutableMap<Rule<*, *>, Boolean>
    ) = computeRulePropertiesHelper(result, seen) { inner.computeRuleProperties(result, seen) }

    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, R>> =
            this.inner.call(consumed, breadcrumbs, chain)
}

class AlternativeRule<T, R>(private val rule1: Rule<T, R>, vararg rules: Rule<T, R>) : Rule<T, R>() {
    override fun computeRuleProperties(
            result: MutableMap<Rule<*, *>, Properties>,
            seen: MutableMap<Rule<*, *>, Boolean>
    ) = computeRulePropertiesHelper(result, seen) {
        val props1 = rule1.computeRuleProperties(result, seen)
        var nullable = props1?.nullable ?: false
        for (rule in rules) {
            val props = rule.computeRuleProperties(result, seen)
            nullable = nullable || (props?.nullable ?: false)
        }
        Properties(nullable)
    }

    private val rules = listOf(*rules)

    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, R>> {
        var result : Chain.NonEmpty<PartialResult<Q, R>> = rule1.call(consumed, breadcrumbs, chain)
        for (parser in rules) {
            result = result + { parser.call(consumed, breadcrumbs, chain) }
        }
        return result
    }
}

class Sequence2Rule<T, A, B, Z> (
        val ruleA: Rule<T, A>,
        val ruleB: Rule<T, B>,
        val constructor: (A, B) -> Z
) : Rule<T, Z>() {
    override fun computeRuleProperties(
            result: MutableMap<Rule<*, *>, Properties>,
            seen: MutableMap<Rule<*, *>, Boolean>
    ) = computeRulePropertiesHelper(result, seen) {
        val propsA = ruleA.computeRuleProperties(result, seen)
        var nullable = propsA?.nullable ?: false
        val propsB = ruleB.computeRuleProperties(result, seen)
        nullable = nullable && (propsB?.nullable ?: false)
        Properties(nullable)
    }

    override fun <Q : T> partialParse(
            consumed: Int,
            breadcrumbs: Map<Rule<*, *>, Int>,
            chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, Z>> =
            // TODO: once KT-18268 is fixed, change this to use a "forRule" helper method
            // TODO: remove type params when Kotlin compiler can infer without crashing
            buildChain<PartialResult<Q, Z>> {
                for (partialA in ruleA.call(consumed, breadcrumbs, chain)) {
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
            } as Chain.NonEmpty<PartialResult<Q, Z>>
}

/**
 * @property consumed how many input tokens were successfully consumed to construct the successful result or before
 * failing
 * @property value either the parsed value, or a `ParseError` in the case of failure
 * @property remaining the remaining chain after the parsed value, or at the point of failure
 */
internal data class PartialResult<out T, out R>(
        val consumed: Int,
        val value: Either<ParseError<T>, R>,
        val remaining: Chain<T>
) : Functor<R> {
    override fun <F> map(f: (R) -> F) = PartialResult(consumed, value.map(f), remaining)
}

val <T> Chain<T>.maybeHead
    get() = when (this) {
        is Chain.NonEmpty -> Maybe.Just(head)
        is Chain.Empty -> Maybe.NOTHING
    }
