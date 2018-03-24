// Copyright © 2017 Laurence Gonsalves
//
// This file is part of xenocom, a library which can be found at
// http://github.com/xenomachina/xenocom
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

package com.xenomachina.common

interface Functor<out E> {
    fun <F> map(f: (E) -> F): Functor<F>
}

/**
 * A `Maybe<T>` can be used where one needs to be able to distinguish between
 * having a T or not having a T, even when T is a nullable type.
 *
 */
sealed class Maybe<out T> : Functor<T> {
    object NOTHING : Maybe<Nothing>() {
        override fun <F> map(f: (Nothing) -> F): Functor<F> = this
    }

    /**
     * @property value the value being held
     */
    data class Just<out T>(val value: T) : Maybe<T>() {
        override fun <F> map(f: (T) -> F) = Just(f(value))
    }
}

/**
 * Dereferences the [Maybe] if it's a [Just], otherwise returns the result of calling [fallback].
 */
fun <T> Maybe<T>.orElse(fallback: () -> T): T = when (this) {
    is Maybe.Just -> value
    else -> fallback()
}

sealed class Either<out L, out R> : Functor<R> {
    data class Left<out L>(val left: L) : Either<L, Nothing>() {
        override fun <F> map(f: (Nothing) -> F): Either<L, F> = this
    }

    data class Right<out R>(val right: R) : Either<Nothing, R>() {
        override fun <F> map(f: (R) -> F): Either<Nothing, F> = Either.Right(f(right))
    }

    abstract override fun <F> map(f: (R) -> F): Either<L, F>
}
