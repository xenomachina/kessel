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

package com.xenomachina.chain

/**
 * A `Chain` represents a sequence of values. Like `Sequence`, a `Chain` is lazy, but unlike `Sequence`, earlier
 * points along the chain can be remembered, which is useful for backtracking. A `Chain` is immutable, modulo laziness.
 */
sealed class Chain<out T> : Iterable<T> {
    /**
     * @property head The first element of the chain.
     */
    class NonEmpty<out T>(val head: T, tailProvider: () -> Chain<T>) : Chain<T>() {
        override fun isEmpty(): Boolean = false

        override fun iterator(): Iterator<T> = ChainIterator(this)

        /**
         * The remaining elements, or null if there are none.
         */
        val tail by lazy(tailProvider)

        operator fun component1(): T = head

        operator fun component2(): Chain<T> = tail

        override fun <F> map(f: (T) -> F): Chain.NonEmpty<F> = NonEmpty(f(head)) { tail.map(f) }
    }

    object Empty : Chain<Nothing>() {
        override fun isEmpty(): Boolean = true

        override fun iterator(): Iterator<Nothing> = emptySequence<Nothing>().iterator()

        override fun <F> map(f: (Nothing) -> F): Empty = this
    }

    abstract fun <F> map(f: (T) -> F): Chain<F>

    abstract fun isEmpty(): Boolean

    abstract override operator fun iterator(): Iterator<T>
}

operator fun <T> Chain<T>.plus(that: () -> Chain<T>): Chain<T> =
    when (this) {
        // These look the same, but they dispatch to the more specifically-typed variants.
        is Chain.Empty -> this + that
        is Chain.NonEmpty<T> -> this + that
    }

operator fun <T, R : Chain<T>> Chain.Empty.plus(that: () -> R): R =
        that()

operator fun <T> Chain.NonEmpty<T>.plus(that: () -> Chain<T>): Chain.NonEmpty<T> =
        Chain.NonEmpty(head) { tail + that }

fun <T> buildChain(builderAction: suspend SequenceScope<T>.() -> Unit): Chain<T> =
        iterator(builderAction).asChain()

class ChainIterator<out T> internal constructor (chain: Chain<T>) : Iterator<T> {
    // For some reason, Kotlin didn't like it when I just used "private set", so simulate it in this roundabout way...
    private var chainVar = chain
    internal val chain: Chain<T>
        get() = chainVar

    override fun hasNext() = !chainVar.isEmpty()

    override fun next() = chainVar.let { s ->
        when (s) {
            is Chain.NonEmpty -> s.head.also { chainVar = s.tail }
            else -> throw NoSuchElementException()
        }
    }
}

/**
 * Returns a chain of the specified elements.
 */
fun <T> chainOf(head: T, vararg tail: T): Chain.NonEmpty<T> = chainOf(head, tail, offset = 0)

/**
 * Returns a chain of no elements.
 */
fun chainOf(): Chain.Empty = Chain.Empty

private fun <T> chainOf(head: T, tail: Array<out T>, offset: Int): Chain.NonEmpty<T> {
    return Chain.NonEmpty(head) {
        if (offset >= tail.size) Chain.Empty
        else chainOf(tail[offset], tail, offset + 1)
    }
}

/**
 * Converts a `Sequence<T>` into a `Chain<T>`. Note that the resulting `Chain` will lazily iterate the `Sequence`.
 */
fun <T> Sequence<T>.asChain(): Chain<T> = iterator().asChain()

/**
 * Converts an `Iterator<T>` into a `Chain<T>`. Note that the resulting `Chain` will lazily iterate the `Iterator`.
 */
fun <T> Iterator<T>.asChain(): Chain<T> =
        when (this) {
            is ChainIterator -> chain
            else -> if (hasNext()) {
                Chain.NonEmpty(next(), this::asChain)
            } else {
                Chain.Empty
            }
        }
