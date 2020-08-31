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

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.None
import arrow.core.Option
import arrow.core.Validated
import arrow.core.ValidatedNel
import com.xenomachina.chain.Chain
import com.xenomachina.chain.asChain
import java.util.IdentityHashMap

typealias ParseResult<T, R> = ValidatedNel<ParseError<T>, R>

class Parser<in T, out R>(private val start: Rule<T, R>) {
    val ruleProps = computeRuleProperties()

    private fun computeRuleProperties(): Map<Rule<*, *>, Rule.Properties> {
        val result = IdentityHashMap<Rule<*, *>, Rule.Properties>()
        val seen = IdentityHashMap<Rule<*, *>, Boolean>()
        start.computeRuleProperties(result, seen)
        for ((key, value) in seen.entries) {
            assert (value) { "$key Properties not computed!?" }
        }
        return result
    }

    private val Rule<*, *>.properties
        get() = ruleProps.get(this)!!

    fun <Q : T> parse(sequence: Sequence<Q>): ParseResult<Q, R> = parse(sequence.asChain())

    private fun <Q : T> parse(chain: Chain<Q>): ParseResult<Q, R> {
        val (head, tail) = start.call(0, IdentityHashMap(), chain)

        return when (head.value) {
            is Validated.Invalid -> {
                data class Accumulated(
                    val errors: NonEmptyList<ParseError<Q>>,
                    var bestConsumed: Int
                )
                tail.fold(Accumulated(NonEmptyList.of(head.value.e), head.consumed)) {
                    accumulated, partial ->
                    when (partial.value) {
                        is Validated.Invalid ->
                            // Collect the errors that get us the furthest into the input.
                            when {
                                partial.consumed > accumulated.bestConsumed -> Accumulated(
                                    errors = NonEmptyList.of(partial.value.e),
                                    bestConsumed = partial.consumed)
                                partial.consumed == accumulated.bestConsumed -> Accumulated(
                                    errors = accumulated.errors.plus(partial.value.e),
                                    bestConsumed = accumulated.bestConsumed)
                                else -> accumulated
                            }

                        is Validated.Valid ->
                            return@parse Validated.Valid(partial.value.a)
                    }
                }.let { Validated.Invalid(it.errors) }
            }
            is Validated.Valid -> Validated.Valid(head.value.a)
        }
    }

    class Builder<in T, out R> (private val block: Builder.Companion.() -> Rule<T, R>) {
        fun build(): Parser<T, R> = Parser(block(Companion))

        companion object {
            /**
             * Matches a single input element if and only if [predicate] returns `true` given that element.
             */
            fun <T> terminal(predicate: (T) -> Boolean) = Terminal(predicate)

            /**
             * Matches a single input element if and only if it is of type `T`.
             */
            inline fun <reified T : Any> isA(): Rule<Any, T> = terminal<Any> { it is T }.map { it as T }

            /**
             * Matches any one of the supplied rules.
             */
            fun <T, R> oneOf(rule1: Rule<T, R>, vararg rules: Rule<T, R>) = AlternativeRule(rule1, *rules)

            /**
             * Matches either of the supplied rules.
             */
            fun <T, Q, R> either(left: Rule<T, Q>, right: Rule<T, R>): Rule<T, Either<Q, R>> =
                AlternativeRule(left.map { Either.left(it) }, right.map { Either.right(it) })

            /**
             * Matches 0 or more of the supplied rule.
             */
            fun <T, R> repeat(rule: Rule<T, R>): Rule<T, List<R>> {
                return object {
                    val me: Rule<T, Chain<R>> = recur { myself }
                    val myself: Rule<T, Chain<R>> =
                        oneOf<T, Chain<R>>(
                            epsilon.map { Chain.Empty },
                            seq(rule, me) { x, chain -> Chain.NonEmpty(x) { chain } }
                        )
                }.myself.map { it.toList() }
            }

            /**
             * Lazily refers to another rule. This is necessary for recursive grammars.
             */
            fun <T, R> recur(inner: () -> Rule<T, R>): Rule<T, R> = LazyRule(inner)

            /**
             * Matches a sequence of one sub-rule. Included for completeness, map is equivalent.
             */
            fun <T, A, Z> seq(
                ruleA: Rule<T, A>,
                constructor: (A) -> Z
            ): Rule<T, Z> = ruleA.map(constructor)

            /**
             * Matches a sequence of two sub-rules.
             */
            fun <T, A, B, Z> seq(
                ruleA: Rule<T, A>,
                ruleB: Rule<T, B>,
                constructor: (A, B) -> Z
            ): Rule<T, Z> = Sequence2Rule(ruleA, ruleB, constructor)

            /**
             * Matches a sequence of three sub-rules.
             */
            fun <T, A, B, C, Z> seq(
                ruleA: Rule<T, A>,
                ruleB: Rule<T, B>,
                ruleC: Rule<T, C>,
                constructor: (A, B, C) -> Z
            ): Rule<T, Z> = Sequence3Rule(ruleA, ruleB, ruleC, constructor)

            /**
             * Matches a sequence of four sub-rules.
             */
            fun <T, A, B, C, D, Z> seq(
                ruleA: Rule<T, A>,
                ruleB: Rule<T, B>,
                ruleC: Rule<T, C>,
                ruleD: Rule<T, D>,
                constructor: (A, B, C, D) -> Z
            ): Rule<T, Z> = Sequence4Rule(ruleA, ruleB, ruleC, ruleD, constructor)

            /**
             * Matches a sequence of five sub-rules.
             */
            fun <T, A, B, C, D, E, Z> seq(
                ruleA: Rule<T, A>,
                ruleB: Rule<T, B>,
                ruleC: Rule<T, C>,
                ruleD: Rule<T, D>,
                ruleE: Rule<T, E>,
                constructor: (A, B, C, D, E) -> Z
            ): Rule<T, Z> = Sequence5Rule(ruleA, ruleB, ruleC, ruleD, ruleE, constructor)

            /**
             * Matches a sequence of six sub-rules.
             */
            fun <T, A, B, C, D, E, F, Z> seq(
                ruleA: Rule<T, A>,
                ruleB: Rule<T, B>,
                ruleC: Rule<T, C>,
                ruleD: Rule<T, D>,
                ruleE: Rule<T, E>,
                ruleF: Rule<T, F>,
                constructor: (A, B, C, D, E, F) -> Z
            ): Rule<T, Z> = Sequence6Rule(ruleA, ruleB, ruleC, ruleD, ruleE, ruleF, constructor)

            /**
             * Matches a sequence of seven sub-rules.
             */
            fun <T, A, B, C, D, E, F, G, Z> seq(
                ruleA: Rule<T, A>,
                ruleB: Rule<T, B>,
                ruleC: Rule<T, C>,
                ruleD: Rule<T, D>,
                ruleE: Rule<T, E>,
                ruleF: Rule<T, F>,
                ruleG: Rule<T, G>,
                constructor: (A, B, C, D, E, F, G) -> Z
            ): Rule<T, Z> = Sequence7Rule(ruleA, ruleB, ruleC, ruleD, ruleE, ruleF, ruleG, constructor)

            fun <T, R> optional(inner: Rule<T, R>): Rule<T, Option<R>> =
                oneOf(epsilon.map { None }, inner.map { Option.just(it) })

            /**
             * Matches zero tokens.
             */
            val epsilon = com.xenomachina.parser.epsilon

            /**
             * Matches the end of input.
             */
            val END_OF_INPUT = com.xenomachina.parser.endOfInput
        }
    }
}
