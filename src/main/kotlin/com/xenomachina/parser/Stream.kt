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

import com.xenomachina.common.Functor

/**
 * A `Stream` represents a sequence of values. Like `Sequence`, a `Stream` is lazy, but unlike `Sequence`, earlier
 * points along the stream can be remembered, which is useful for backtracking.
 *
 * A `Stream` always contains at least one value. An empty sequence is represented as `null`, and hence a potentially
 * empty sequence is represented as a `Stream<T>?`.
 */
interface Stream<out T> : Functor<T> {
    /**
     * The first element of the stream.
     */
    val head: T

    /**
     * The remaining elements, or null if there are none.
     */
    val tail: Stream<T>?

    override fun <F> map(f: (T) -> F): Stream<F> = let { parent ->
        object : Stream<F> {
            override val head = f(parent.head)
            override val tail by lazy { parent.tail?.map(f) }
        }
    }
}

operator fun <T> Stream<T>?.iterator() = let { self ->
    object : Iterator<T> {
        var stream = self

        override fun hasNext() = (stream == null)

        override fun next() = stream.let { s ->
                if (s == null) {
                    throw NoSuchElementException()
                } else {
                    s.head.also { stream = s.tail }
                }
            }
    }
}


fun <T> Stream<T>.component1() = head
fun <T> Stream<T>.component2() = tail

/**
 * Returns a new stream of the specified elements.
 */
fun <T> streamOf(head: T, vararg tail: T): Stream<T> = streamOf(head, tail, offset = 0)

private fun <T> streamOf(head: T, tail: Array<out T>, offset: Int): Stream<T> {
    return object : Stream<T> {
        override val head = head
        override val tail by lazy {
            if (offset >= tail.size) null
            else streamOf(tail[offset], tail, offset + 1)
        }
    }
}

/**
 * Converts a `Sequence<T>` into a `Stream<T>`. Note that the resulting `Stream` will lazily iterate the `Sequence`.
 */
fun <T> Sequence<T>.asStream(): Stream<T>? = iterator().asStream()

private fun <T> Iterator<T>.asStream(): Stream<T>? {
    // TODO: if iterator comes from a Stream, just return that stream.
    return if (hasNext()) {
        object : Stream<T> {
            override val head = next()
            override val tail by lazy { asStream() }
        }
    } else null
}
