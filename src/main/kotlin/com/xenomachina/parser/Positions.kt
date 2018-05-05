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

data class Positioned<P, T>(
    val start: P,
    val value: T,
    val end: P
)

interface PositionTracker<P> {
    fun start(): P
    fun next(current: P, s: CharSequence): P
}

/**
 * A `PositionTracker` that doesn't track anything.
 */
object NoOpPositionTracker : PositionTracker<Unit> {
    override fun start() {}
    override fun next(current: Unit, s: CharSequence) {}
}

/**
 * A `PositionTracker` that just tracks character offset.
 */
object CharOffsetTracker : PositionTracker<Int> {
    override fun start(): Int = 0
    override fun next(current: Int, s: CharSequence) = current + s.length
}
