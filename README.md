# Kessel — Parser Combinators for Kotlin

[![Project Status: WIP – Initial development is in progress, but there has not yet been a stable, usable release suitable for the public.](http://www.repostatus.org/badges/latest/wip.svg)](http://www.repostatus.org/#wip)
[![Build Status](https://travis-ci.org/xenomachina/kessel.svg?branch=master)](https://travis-ci.org/xenomachina/kessel)
[![License: LGPL 2.1](https://img.shields.io/badge/license-LGPL--2.1-blue.svg)](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html)
<!-- [![Maven Central](https://img.shields.io/maven-central/v/com.xenomachina/kessel.svg)](https://mvnrepository.com/artifact/com.xenomachina/kessel) -->
<!-- TODO: [![codebeat badge](https://codebeat.co/badges/902174e2-31be-4f9d-a4ba-40178b075d2a)](https://codebeat.co/projects/github-com-xenomachina-kessel-master)-->
<!-- TODO: [![Javadocs](https://www.javadoc.io/badge/com.xenomachina/kessel.svg)](https://www.javadoc.io/doc/com.xenomachina/kessel) -->

Kessel is a simple parser combinator library for Kotlin.

**This library is currently a work in progress. Feel free to try it out, and
comments/suggestions/contributions are welcome, but incompatible changes may be
made without notice.**

Like many other parsers, Kessel has two main stages:

- tokenization, which converts a sequence of characters into a sequence of
  tokens

- parsing, which converts a sequence of tokens into an abstract syntax tree

These two stages are very loosely coupled. The types for tokens and abstract
syntax trees are both user-defined, and one can easily use tokenization without
parsing, parsing without tokenizing, or perform additional processing on the
token stream between tokenization and parsing.

## Tokenization

Your tokens can be whatever type you want, but they need to have a common
supertype, so it's natural to use a sealed class. For example:

``` kotlin
sealed class MathToken {
    sealed class Value : MathToken() {
        data class Identifier(val name: String) : MathToken.Value()
        data class IntLiteral(val value: Int) : MathToken.Value()
        data class FloatLiteral(val value: Double) : MathToken.Value()
    }

    sealed class Operator : MathToken() {
        data class MultOp(val name: String) : MathToken.Operator()
        data class AddOp(val name: String) : MathToken.Operator()
    }

    object OpenParen : MathToken()
    object CloseParen : MathToken()

    data class Space(val spaces: String) : MathToken()
}
```

Tokenization is performed with `RegexTokenizer`. You'll need a `Regex` for
each type of token, and a way to convert from a regex `MatchResult` into a
token object.

``` kotlin
val MATH_TOKENIZER = RegexTokenizer<MathToken>(
    Regex("\\p{Alpha}[\\p{Alpha}0-9]+") to { m -> MathToken.Value.Identifier(m.group()) },
    Regex("\\d+") to { m -> MathToken.Value.IntLiteral(m.group().toInt()) },
    Regex("\\d*\\.\\d") to { m -> MathToken.Value.FloatLiteral(m.group().toDouble()) },
    Regex("\\s+") to { m -> MathToken.Space(m.group()) },
    Regex("[*/]") to { m -> MathToken.Operator.MultOp(m.group()) },
    Regex("[-+]") to { m -> MathToken.Operator.AddOp(m.group()) },
    Regex("\\(") to { _ -> MathToken.OpenParen },
    Regex("\\)") to { _ -> MathToken.CloseParen }
)```

When multiple regexes match the input, the longest match wins.

To tokenize a `CharSequence`:

``` kotlin
MATH_TOKENIZER.tokenize("foo bar 123 baz789 45.6 45 .6 hello")
```

This will return a `Sequence<MathToken>`.

## Parsing

Suppose we want to parse a sequence of `MathToken` objects from the previous
section into an expression tree. Our expression tree might be modelled as:

``` kotlin
sealed class Expr {
    data class Op(val left: Expr, val op: MathToken.Operator, val right: Expr) : Expr()
    data class Leaf(val value: MathToken.Value) : Expr()
}
```

A parser is built using a `Parser.Builder`:

``` kotlin
val parser = Parser.Builder {
    val grammar = object {
        val multOp = isA<MathToken.Operator.MultOp>()

        val addOp = isA<MathToken.Operator.AddOp>()

        val factor = oneOf(
                    isA<MathToken.Value.IntLiteral>().map(Expr::Leaf),
                    isA<MathToken.Value.Identifier>().map(Expr::Leaf),
                    seq(
                            isA<MathToken.OpenParen>(),
                            recur { expression },
                            isA<MathToken.CloseParen>()
                    ) { _, expr, _ -> expr }
            )

        val term: Rule<MathToken, Expr> by lazy {
            oneOf(
                    factor,
                    seq(factor, multOp, recur { term }) { l, op, r -> Expr.Op(l, op, r) }
            )
        }

        val expression: Rule<MathToken, Expr> by lazy {
            oneOf(
                    term,
                    seq(term, addOp, recur { expression }) { l, op, r -> Expr.Op(l, op, r) }
            )
        }

        val start = seq(expression, END_OF_INPUT) { expr, _ -> expr }
    }

    multRule = grammar.multOp
    exprRule = grammar.expression

    grammar.start
}.build()
```

Each parsing rule is a variable of type `Rule<T, R>`. Most of the rules we
place inside of an `object` expression, named `grammar` by convention. This is
done to allow cyclic references. For grammars without cyclic references it
would be possible to just use `val` statements directly in the `Builder` block.

The final line of the block evaluates to the start rule of our grammar.

In the `Builder` are several helpers for constructing your grammar:

- `epsilon` - matches zero input elements. Always succeeds, and returns `Unit`.

- `terminal(predicate)` - matches a single input element using the supplied predicate.

- `isA<T>()` - a `terminal` that matches if the element is of type `T`.

- `oneOf(rules...)` - a rule that matches one of the supplied rules. Like a
  logical "or". The rules must all return a common type.

- `either(left, right)` - a rule that matches one of the supplied rules, just
  like `oneOf`, except the rules do not need to return a common type. Instead,
  they are wrapped in an `Either`.

- `seq(rules...){ a, b, c, ... -> result }` - a rule that matches a number of
  other rules in sequence. Unlike many other parser combinator implementations
  that require chaining multiple "and" constructs, `seq` can take between 1 and
  7 sub-rules, and passes all of their parses to the supplied parser.

- `recur(rule)` - used to lazily refer to another rule. This is necessary for
  recursive grammars.

- `END_OF_INPUT` - a rule that matches only the end of the input

`Rule` objects are also mappable. That is, the parsed value returned by a
`Rule` can be transformed using the `map` method.

Parsing is then performed by passing a sequence of tokens into the parser's
`parse` method:

``` kotlin
val parse = parser.parse(MATH_TOKENIZER.tokenize("5 * (3 + 7) - (4 / (2 - 1))")
        .filterNot { it is MathToken.Space })
```

This returns a `ParseResult<T, R>`, where `T` is the token type (that is, the
type of element in the input `Sequence`), and `R` is the return type of the
parser's start rule.

`ParseResult` is defined as:

``` kotlin
typealias ParseResult<T, R> = Either<List<ParseError<T>>, R>
```

## Position Tracking

Kessel includes some helpers for identifying the original position of
tokenized/parsed objects in the source, to assist with error reporting and
other diagnostics.

When tokenizing, a `PositionTracker` can be supplied:

``` kotlin
MATH_TOKENIZER.tokenize(postionTracker, "foo bar 123 baz789 45.6 45 .6 hello")
```

This will return a `Sequence<Positioned<P, MathToken>>`, where `P` is the
position type of the supplied `PostionTracker`.

Parsers must be written specifically to be able to handle `Positioned`
tokens. (I'd like to find a way to make this more transparent, but I'm not sure
if that's even possible yet.)
