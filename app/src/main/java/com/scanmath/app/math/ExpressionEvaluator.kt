package com.scanmath.app.math

import kotlin.math.pow

/**
 * A small, dependency-free recursive-descent evaluator for arithmetic
 * expressions. Supports + - * / ^ ( ), unary minus and decimals.
 *
 * Grammar:
 *   expression := term (('+' | '-') term)*
 *   term       := power (('*' | '/' | '%') power)*
 *   power      := unary ('^' unary)*
 *   unary      := ('+' | '-') unary | primary
 *   primary    := number | '(' expression ')'
 */
class ExpressionEvaluator(private val input: String) {

    private var pos = 0

    fun evaluate(): Double {
        val result = parseExpression()
        skipSpaces()
        if (pos < input.length) {
            throw ArithmeticException("Unexpected character '${input[pos]}' at position $pos")
        }
        return result
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipSpaces()
            when (peek()) {
                '+' -> { pos++; value += parseTerm() }
                '-' -> { pos++; value -= parseTerm() }
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parsePower()
        while (true) {
            skipSpaces()
            when (peek()) {
                '*' -> { pos++; value *= parsePower() }
                '/' -> {
                    pos++
                    val divisor = parsePower()
                    if (divisor == 0.0) throw ArithmeticException("Division by zero")
                    value /= divisor
                }
                '%' -> {
                    pos++
                    val divisor = parsePower()
                    if (divisor == 0.0) throw ArithmeticException("Modulo by zero")
                    value %= divisor
                }
                else -> return value
            }
        }
    }

    private fun parsePower(): Double {
        val base = parseUnary()
        skipSpaces()
        if (peek() == '^') {
            pos++
            val exponent = parsePower() // right associative
            return base.pow(exponent)
        }
        return base
    }

    private fun parseUnary(): Double {
        skipSpaces()
        return when (peek()) {
            '-' -> { pos++; -parseUnary() }
            '+' -> { pos++; parseUnary() }
            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): Double {
        skipSpaces()
        val c = peek() ?: throw ArithmeticException("Unexpected end of expression")
        if (c == '(') {
            pos++
            val value = parseExpression()
            skipSpaces()
            if (peek() != ')') throw ArithmeticException("Missing closing parenthesis")
            pos++
            return value
        }
        return parseNumber()
    }

    private fun parseNumber(): Double {
        skipSpaces()
        val start = pos
        var sawDigit = false
        var sawDot = false
        while (pos < input.length) {
            val ch = input[pos]
            if (ch.isDigit()) {
                sawDigit = true; pos++
            } else if (ch == '.' && !sawDot) {
                sawDot = true; pos++
            } else break
        }
        if (!sawDigit) throw ArithmeticException("Expected a number at position $start")
        return input.substring(start, pos).toDouble()
    }

    private fun peek(): Char? = if (pos < input.length) input[pos] else null

    private fun skipSpaces() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }

    companion object {
        /** Convenience: returns null instead of throwing on invalid input. */
        fun tryEvaluate(expression: String): Double? = try {
            ExpressionEvaluator(expression).evaluate()
        } catch (e: Exception) {
            null
        }
    }
}
