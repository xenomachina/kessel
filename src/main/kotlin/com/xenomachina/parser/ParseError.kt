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

/**
 * @property message error message
 */
class ParseError<out T>(val consumed: Int, val element: Option<T>, message: () -> String) {
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
