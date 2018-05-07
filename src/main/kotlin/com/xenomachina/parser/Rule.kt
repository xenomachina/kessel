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

import arrow.core.Option
import arrow.data.Validated
import com.xenomachina.chain.Chain
import com.xenomachina.chain.buildChain
import com.xenomachina.chain.chainOf
import com.xenomachina.chain.plus
import java.util.IdentityHashMap
import kotlin.coroutines.experimental.SequenceBuilder

abstract class Rule<in T, out R> {
    internal abstract fun <Q : T> partialParse(
        consumed: Int,
        // TODO: change breadcrumbs to use a Chain instead of Map?
        breadcrumbs: Map<Rule<*, *>, Int>,
        chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, R>>

    // TODO: rename this to invoke???
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
    crossinline body: () -> Rule.Properties?
): Rule.Properties? {
    val props: Rule.Properties?
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

fun <T, A, B> Rule<T, A>.map(transform: (A) -> B): Rule<T, B> = MappingRule<T, A, B>(this, transform)

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

/**
 * A [Rule] tha matches zero tokens.
 */
object epsilon : Rule<Any?, Unit>() {
    override fun computeRuleProperties(
        result: MutableMap<Rule<*, *>, Properties>,
        seen: MutableMap<Rule<*, *>, Boolean>
    ) = Properties(nullable = true)

    override fun <Q> partialParse(
        consumed: Int,
        breadcrumbs: Map<Rule<*, *>, Int>,
        chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, Unit>> =
            chainOf(PartialResult( consumed, Validated.Valid(Unit), chain))
}

object endOfInput : Rule<Any?, Unit>() {
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
                    chainOf<PartialResult<Q, Unit>>(PartialResult(consumed, Validated.Valid(Unit), chain))

                is Chain.NonEmpty ->
                    chainOf(PartialResult(
                            consumed,
                            Validated.Invalid(ParseError(consumed, chain.maybeHead) { "Expected end of input, found: ${chain.head}" }),
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
                            Validated.Invalid(ParseError(consumed, chain.maybeHead) { "Unexpected end of input" }),
                            chain))

                is Chain.NonEmpty ->
                    if (predicate(chain.head)) {
                        chainOf(PartialResult(
                                consumed + 1,
                                Validated.Valid(chain.head),
                                chain.tail))
                    } else {
                        chainOf(PartialResult(
                                consumed,
                                Validated.Invalid(ParseError(consumed, chain.maybeHead) { "Unexpected: ${chain.head}" }),
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
        var result: Chain.NonEmpty<PartialResult<Q, R>> = rule1.call(consumed, breadcrumbs, chain)
        for (parser in rules) {
            result = result + { parser.call(consumed, breadcrumbs, chain) }
        }
        return result
    }
}

sealed class SequenceRule<T, Z> (
    private val ruleA: Rule<T, *>,
    private vararg val rules: Rule<T, *>
) : Rule<T, Z>() {
    override fun computeRuleProperties(
        result: MutableMap<Rule<*, *>, Properties>,
        seen: MutableMap<Rule<*, *>, Boolean>
    ) = computeRulePropertiesHelper(result, seen) {
        val propsA = ruleA.computeRuleProperties(result, seen)
        var nullable = propsA?.nullable ?: false
        for (rule in rules) {
            val props = rule.computeRuleProperties(result, seen)
            nullable = nullable && (props?.nullable ?: false)
        }
        Properties(nullable)
    }
}

class Sequence2Rule<T, A, B, Z>(
    val ruleA: Rule<T, A>,
    val ruleB: Rule<T, B>,
    val constructor: (A, B) -> Z
) : SequenceRule<T, Z>(ruleA, ruleB) {
    override fun <Q : T> partialParse(
        consumed: Int,
        breadcrumbs: Map<Rule<*, *>, Int>,
        chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, Z>> =
    // TODO: remove type params when Kotlin compiler can infer without crashing
            buildChain<PartialResult<Q, Z>> {
                forSequenceSubRule(ruleA, consumed, breadcrumbs, chain) { partialA, a ->
                    forSequenceSubRule(ruleB, partialA.consumed, breadcrumbs, partialA.remaining) { partialB, b ->
                        yield(PartialResult(consumed,
                                Validated.Valid(constructor(a, b)),
                                partialB.remaining))
                    }
                }
            } as Chain.NonEmpty<PartialResult<Q, Z>>
}

class Sequence3Rule<T, A, B, C, Z>(
    val ruleA: Rule<T, A>,
    val ruleB: Rule<T, B>,
    val ruleC: Rule<T, C>,
    val constructor: (A, B, C) -> Z
) : SequenceRule<T, Z>(ruleA, ruleB, ruleC) {
    override fun <Q : T> partialParse(
        consumed: Int,
        breadcrumbs: Map<Rule<*, *>, Int>,
        chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, Z>> =
    // TODO: remove type params when Kotlin compiler can infer without crashing
            buildChain<PartialResult<Q, Z>> {
                forSequenceSubRule(ruleA, consumed, breadcrumbs, chain) { partialA, a ->
                    forSequenceSubRule(ruleB, partialA.consumed, breadcrumbs, partialA.remaining) { partialB, b ->
                        forSequenceSubRule(ruleC, partialB.consumed, breadcrumbs, partialB.remaining) { partialC, c ->
                            yield(PartialResult(consumed,
                                    Validated.Valid(constructor(a, b, c)),
                                    partialC.remaining))
                        }
                    }
                }
            } as Chain.NonEmpty<PartialResult<Q, Z>>
}

class Sequence4Rule<T, A, B, C, D, Z>(
    val ruleA: Rule<T, A>,
    val ruleB: Rule<T, B>,
    val ruleC: Rule<T, C>,
    val ruleD: Rule<T, D>,
    val constructor: (A, B, C, D) -> Z
) : SequenceRule<T, Z>(ruleA, ruleB, ruleC) {
    override fun <Q : T> partialParse(
        consumed: Int,
        breadcrumbs: Map<Rule<*, *>, Int>,
        chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, Z>> =
    // TODO: remove type params when Kotlin compiler can infer without crashing
            buildChain<PartialResult<Q, Z>> {
                forSequenceSubRule(ruleA, consumed, breadcrumbs, chain) { partialA, a ->
                    forSequenceSubRule(ruleB, partialA.consumed, breadcrumbs, partialA.remaining) { partialB, b ->
                        forSequenceSubRule(ruleC, partialB.consumed, breadcrumbs, partialB.remaining) { partialC, c ->
                            forSequenceSubRule(ruleD, partialC.consumed, breadcrumbs, partialC.remaining) { partialD, d ->
                                yield(PartialResult(
                                        consumed, Validated.Valid(constructor(a, b, c, d)), partialD.remaining))
                            }
                        }
                    }
                }
            } as Chain.NonEmpty<PartialResult<Q, Z>>
}

class Sequence5Rule<T, A, B, C, D, E, Z>(
    val ruleA: Rule<T, A>,
    val ruleB: Rule<T, B>,
    val ruleC: Rule<T, C>,
    val ruleD: Rule<T, D>,
    val ruleE: Rule<T, E>,
    val constructor: (A, B, C, D, E) -> Z
) : SequenceRule<T, Z>(ruleA, ruleB, ruleC) {
    override fun <Q : T> partialParse(
        consumed: Int,
        breadcrumbs: Map<Rule<*, *>, Int>,
        chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, Z>> =
    // TODO: remove type params when Kotlin compiler can infer without crashing
            buildChain<PartialResult<Q, Z>> {
                forSequenceSubRule(ruleA, consumed, breadcrumbs, chain) { partialA, a ->
                    forSequenceSubRule(ruleB, partialA.consumed, breadcrumbs, partialA.remaining) { partialB, b ->
                        forSequenceSubRule(ruleC, partialB.consumed, breadcrumbs, partialB.remaining) { partialC, c ->
                            forSequenceSubRule(ruleD, partialC.consumed, breadcrumbs, partialC.remaining) { partialD, d ->
                                forSequenceSubRule(ruleE, partialD.consumed, breadcrumbs, partialD.remaining) { partialE, e ->
                                    yield(PartialResult(consumed,
                                            Validated.Valid(constructor(a, b, c, d, e)),
                                            partialE.remaining))
                                }
                            }
                        }
                    }
                }
            } as Chain.NonEmpty<PartialResult<Q, Z>>
}

class Sequence6Rule<T, A, B, C, D, E, F, Z>(
    val ruleA: Rule<T, A>,
    val ruleB: Rule<T, B>,
    val ruleC: Rule<T, C>,
    val ruleD: Rule<T, D>,
    val ruleE: Rule<T, E>,
    val ruleF: Rule<T, F>,
    val constructor: (A, B, C, D, E, F) -> Z
) : SequenceRule<T, Z>(ruleA, ruleB, ruleC) {
    override fun <Q : T> partialParse(
        consumed: Int,
        breadcrumbs: Map<Rule<*, *>, Int>,
        chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, Z>> =
    // TODO: remove type params when Kotlin compiler can infer without crashing
            buildChain<PartialResult<Q, Z>> {
                forSequenceSubRule(ruleA, consumed, breadcrumbs, chain) { partialA, a ->
                    forSequenceSubRule(ruleB, partialA.consumed, breadcrumbs, partialA.remaining) { partialB, b ->
                        forSequenceSubRule(ruleC, partialB.consumed, breadcrumbs, partialB.remaining) { partialC, c ->
                            forSequenceSubRule(ruleD, partialC.consumed, breadcrumbs, partialC.remaining) { partialD, d ->
                                forSequenceSubRule(ruleE, partialD.consumed, breadcrumbs, partialD.remaining) { partialE, e ->
                                    forSequenceSubRule(ruleF, partialE.consumed, breadcrumbs, partialE.remaining) { partialF, f ->
                                        yield(PartialResult(consumed,
                                                Validated.Valid(constructor(a, b, c, d, e, f)),
                                                partialF.remaining))
                                    }
                                }
                            }
                        }
                    }
                }
            } as Chain.NonEmpty<PartialResult<Q, Z>>
}

class Sequence7Rule<T, A, B, C, D, E, F, G, Z>(
    val ruleA: Rule<T, A>,
    val ruleB: Rule<T, B>,
    val ruleC: Rule<T, C>,
    val ruleD: Rule<T, D>,
    val ruleE: Rule<T, E>,
    val ruleF: Rule<T, F>,
    val ruleG: Rule<T, G>,
    val constructor: (A, B, C, D, E, F, G) -> Z
) : SequenceRule<T, Z>(ruleA, ruleB, ruleC) {
    override fun <Q : T> partialParse(
        consumed: Int,
        breadcrumbs: Map<Rule<*, *>, Int>,
        chain: Chain<Q>
    ): Chain.NonEmpty<PartialResult<Q, Z>> =
    // TODO: remove type params when Kotlin compiler can infer without crashing
            buildChain<PartialResult<Q, Z>> {
                forSequenceSubRule(ruleA, consumed, breadcrumbs, chain) { partialA, a ->
                    forSequenceSubRule(ruleB, partialA.consumed, breadcrumbs, partialA.remaining) { partialB, b ->
                        forSequenceSubRule(ruleC, partialB.consumed, breadcrumbs, partialB.remaining) { partialC, c ->
                            forSequenceSubRule(ruleD, partialC.consumed, breadcrumbs, partialC.remaining) { partialD, d ->
                                forSequenceSubRule(ruleE, partialD.consumed, breadcrumbs, partialD.remaining) { partialE, e ->
                                    forSequenceSubRule(ruleF, partialE.consumed, breadcrumbs, partialE.remaining) { partialF, f ->
                                        forSequenceSubRule(ruleG, partialF.consumed, breadcrumbs, partialF.remaining) { partialG, g ->
                                            yield(PartialResult(consumed,
                                                    Validated.Valid(constructor(a, b, c, d, e, f, g)),
                                                    partialG.remaining))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } as Chain.NonEmpty<PartialResult<Q, Z>>
}

private suspend inline fun <T, Z, Q : T, R> SequenceBuilder<PartialResult<Q, Z>>.forSequenceSubRule(
    rule: Rule<T, R>,
    consumed: Int,
    breadcrumbs: Map<Rule<*, *>, Int>,
    chain: Chain<Q>,
    crossinline body: suspend SequenceBuilder<PartialResult<Q, Z>>.(PartialResult<Q, R>, R) -> Unit
) {
    for (partial in rule.call(consumed, breadcrumbs, chain)) {
        when (partial.value) {
            is Validated.Invalid -> {
                // TODO: This object should be identical to partial.value, but we have to rebuild it to get the types
                // right. An unchecked cast would probably work here.
                yield(PartialResult<Q, Z>(partial.consumed, partial.value, partial.remaining))
            }
            is Validated.Valid -> {
                body(partial, partial.value.a)
            }
        }
    }
}

// TODO: flip this inside-out so that Validated is on the outside? Then R can be Nothing in case of error.
/**
 * @property consumed how many input tokens were successfully consumed to construct the successful result or before
 * failing
 * @property value either the parsed value, or a `ParseError` in the case of failure
 * @property remaining the remaining chain after the parsed value, or at the point of failure
 */
internal data class PartialResult<out T, out R>(
    val consumed: Int,
    val value: Validated<ParseError<T>, R>,
    val remaining: Chain<T>
) {
    fun <F> map(f: (R) -> F) = PartialResult(consumed, value.map(f), remaining)
}

val <T> Chain<T>.maybeHead
    get() = when (this) {
        is Chain.NonEmpty -> Option.just(head)
        is Chain.Empty -> Option.empty()
    }
