package com.scanmath.app.math

/**
 * Turns raw OCR text (as produced by ML Kit) into one or more clean
 * arithmetic expressions, then evaluates them.
 *
 * Layouts handled:
 *   1. Inline:        "10 + 30 + 40"
 *   2. Stacked sum:   "100" / "+ 25" / "+ 45" / "- 20"
 *   3. Plain column:  "100" / "25" / "45" / "20"  (no symbols => added together)
 *
 * Rule of thumb: when no arithmetic symbol joins two numbers, they are ADDED.
 * Multiple independent problems may be separated by an "(or)" marker or a blank
 * line; each is returned as its own result.
 */
object MathParser {

    data class Result(
        val expression: String,
        val value: Double?,
        val error: String? = null
    )

    private val NUMBER = Regex("\\d*\\.?\\d+")

    /** Common OCR glyph confusions for digits (e.g. the letter O read as 0). */
    private val LOOKALIKE = mapOf(
        'O' to '0', 'o' to '0', 'Q' to '0', 'D' to '0',
        'I' to '1', 'l' to '1', 'i' to '1', '|' to '1', '!' to '1',
        'Z' to '2', 'z' to '2',
        'S' to '5', 's' to '5',
        'b' to '6',
        'B' to '8',
        'g' to '9', 'q' to '9'
    )

    /** Normalize operator glyphs and drop thousands separators (one line). */
    private fun normalizeSymbols(raw: String): String {
        val sb = StringBuilder()
        for (c in raw) {
            when (c) {
                '×', 'x', 'X', '✕', '∗', '·', '•' -> sb.append('*')
                '÷', '∕', '⁄' -> sb.append('/')
                '−', '–', '—', '－' -> sb.append('-')
                '＋' -> sb.append('+')
                ',' -> { /* thousands separator: drop */ }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Repair a token that should be a number but was misread (e.g. "l00" -> "100"). */
    private fun digitizeToken(token: String): String {
        if (token.any { it.isDigit() }) {
            // Has at least one digit: fix look-alike letters, keep operators/parens.
            return token.map { c -> if (c.isLetter()) LOOKALIKE[c] ?: c else c }
                .joinToString("")
        }
        // No digit: only convert if the whole token maps to a clean number.
        val mapped = token.map { LOOKALIKE[it] ?: it }.joinToString("")
        return if (NUMBER.matches(mapped)) mapped else token
    }

    /** Keep only characters valid inside an arithmetic expression. */
    private fun sanitize(line: String): String =
        line.filter { it.isDigit() || it in "+-*/%^(). " }
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Symbol-normalize, repair digits, and sanitize a single OCR line. */
    private fun cleanLine(raw: String): String {
        val symbolized = normalizeSymbols(raw).trim()
        if (symbolized.isEmpty()) return ""
        val repaired = symbolized.split(Regex("\\s+"))
            .joinToString(" ") { digitizeToken(it) }
        return sanitize(repaired)
    }

    /** True if the line carries its own binary operator (so it's a standalone problem). */
    private fun hasBinaryOperator(s: String): Boolean {
        val withoutLeading = s.trimStart(' ', '+', '-', '*', '/', '%', '^')
        return withoutLeading.any { it in "+-*/%^" }
    }

    /** Insert a '+' between any two adjacent operands (the "no symbol => add" rule). */
    private fun insertImplicitPlus(expr: String): String {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(expr.substring(start, i))
                }
                else -> { tokens.add(c.toString()); i++ }
            }
        }
        val sb = StringBuilder()
        var prev: String? = null
        for (t in tokens) {
            if (prev != null) {
                val prevEndsValue = prev.last().isDigit() || prev == ")" || prev.last() == '.'
                val curStartsValue = t[0].isDigit() || t == "("
                if (prevEndsValue && curStartsValue) sb.append('+')
            }
            sb.append(t)
            prev = t
        }
        return sb.toString()
    }

    /** Group OCR lines into problems and stitch stacked/column sums together. */
    private fun buildExpressions(text: String): List<String> {
        val problems = mutableListOf<String>()
        val block = StringBuilder()

        fun flushBlock() {
            val expr = insertImplicitPlus(block.toString())
            if (expr.any { it.isDigit() }) problems.add(expr)
            block.setLength(0)
        }

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            val compact = line.replace("(", "").replace(")", "").trim()
            if (line.isEmpty() || compact.equals("or", ignoreCase = true)) {
                flushBlock()
                continue
            }
            val cleaned = cleanLine(line)
            if (cleaned.isEmpty()) continue

            if (hasBinaryOperator(cleaned)) {
                // A self-contained equation: close any running column first.
                flushBlock()
                val expr = insertImplicitPlus(cleaned)
                if (expr.any { it.isDigit() }) problems.add(expr)
            } else {
                // Just a number (or a "+25"-style continuation): part of the column.
                if (block.isNotEmpty()) block.append(' ')
                block.append(cleaned)
            }
        }
        flushBlock()
        return problems
    }

    /** Parse + evaluate every problem found in the recognized text. */
    fun parseAndEvaluate(text: String): List<Result> {
        return buildExpressions(text).map { expr ->
            try {
                Result(expr, ExpressionEvaluator(expr).evaluate())
            } catch (e: Exception) {
                Result(expr, null, e.message ?: "Could not evaluate")
            }
        }
    }

    /** Format a Double without a trailing ".0" for whole numbers. */
    fun format(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            "%.4f".format(value).trimEnd('0').trimEnd('.')
        }
    }
}
