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

import java.util.regex.MatchResult
import java.util.regex.Matcher
import kotlin.coroutines.experimental.buildSequence

/**
 * Constructs a token from a [MatchResult].
 */
typealias TokenConstructor<T> = (MatchResult) -> T

/**
 * A Tokenizer converts a [CharSequence] into a [Sequence] of tokens. It acts
 * as a mapping from [Regex] objects to [TokenConstructor] objects.
 *
 * A Tokenizer is stateless and renetrant. It is safe to reuse a Tokenizer or even use a single Tokenizer on multiple
 * sequences concurrently.
 */
class Tokenizer<P, T>(
    private val posTracker: PositionTracker<P>,
    vararg regexToToken: Pair<Regex, TokenConstructor<T>>
) {
    private val patternsToHandlers =
        regexToToken.map { (regex, f) -> regex.toPattern() to f }.toList()

    /**
     * Converts the specified [CharSequence] into a [Sequence] of tokens of
     * type `T`. Starting at the beginning of the `CharSequence`, each `Regex`
     * is tested for a match, and the `TokenConstructor` associated with the
     * longest match is used to construct a token. It then advances to the next
     * position in the `CharSequence`.
     */
    fun tokenize(s: CharSequence): Sequence<Positioned<P, T>> = buildSequence {
        // TODO: add priorities?
        val length = s.length
        val matchersToHandlers = patternsToHandlers.map { (pattern, f) -> pattern.matcher(s) to f }

        var index = 0
        var pos = posTracker.start()
        while (index < length) {
            var bestMatcherToHandler: Pair<Matcher, TokenConstructor<T>>? = null
            var bestLen = 0
            for (matcherToHandler in matchersToHandlers) {
                val matcher = matcherToHandler.first
                matcher.region(index, length)
                if (matcher.lookingAt()) {
                    val matchLen = matcher.end() - matcher.start()
                    // TODO: warn if matchLen == 0?
                    if (matchLen > bestLen) {
                        bestMatcherToHandler = matcherToHandler
                        bestLen = matchLen
                    } else if (bestMatcherToHandler != null && matchLen == bestLen) {
                        TODO(
                            "Ambiguous tokenization: /${matcher.pattern().pattern()}/ and" +
                            " /${bestMatcherToHandler.first.pattern().pattern()}/ both match $bestLen characters" +
                            " at $pos")
                    }
                }
            }
            if (bestMatcherToHandler == null) {
                TODO("add fallback handling")
            } else {
                val (matcher, handler) = bestMatcherToHandler
                val nextPos = posTracker.next(pos, matcher.group())
                yield(Positioned(pos, handler(matcher.toMatchResult()), nextPos))
                pos = nextPos
                index += bestLen
            }
        }
    }
}
