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
import com.xenomachina.chain.Chain
import java.util.IdentityHashMap
import kotlin.reflect.KClass

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

    fun <Q : T> parse(chain: Chain<Q>): Either<List<ParseError<Q>>, R> {
        val breadcrumbs = IdentityHashMap<Rule<*, *>, Int>()
        val errors = mutableListOf<ParseError<Q>>()
        var bestConsumed = 0
        for (partial in start.call(0, breadcrumbs, chain)) {
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

    class Builder<in T, out R> (private val body: Builder.Companion.() -> Rule<T, R>) {
        fun build(): Parser<T, R> = Parser(body(Companion))

        companion object {
            fun <T : Any> isA(kclass: KClass<T>): Rule<Any, T> {
                val javaClass = kclass.java
                return terminal<Any> { javaClass.isInstance(it) }.map { javaClass.cast(it) }
            }

            fun <T> terminal(predicate: (T) -> Boolean) = Terminal<T>(predicate)

            fun <T, R> oneOf(rule1: Rule<T, R>, vararg rules: Rule<T, R>) = AlternativeRule(rule1, *rules)

            fun <T, R> L(inner: () -> Rule<T, R>): Rule<T, R> = LazyRule(inner)

            fun <T, A, B, Z> seq(
                    ruleA: Rule<T, A>,
                    ruleB: Rule<T, B>,
                    constructor: (A, B) -> Z
            ): Rule<T, Z> = Sequence2Rule(ruleA, ruleB, constructor)

            // TODO: inline to remove Pair construction
            // TODO: add variants up to 7 parameters
            fun <T, A, B, C, Z> seq(
                    ruleA: Rule<T, A>,
                    ruleB: Rule<T, B>,
                    ruleC: Rule<T, C>,
                    f: (A, B, C) -> Z
            ): Rule<T, Z> =
                    seq(ruleA, seq(ruleB, ruleC) { b, c -> Pair(b, c) }) { a, b_c -> f(a, b_c.first, b_c.second) }

            val END_OF_INPUT = com.xenomachina.parser.END_OF_INPUT
        }
    }
}
