package com.scanmath.app.math

/**
 * Turns raw OCR text (as produced by ML Kit) into one or more clean
 * arithmetic expressions, then evaluates them.
 *
 * It handles two common layouts seen on paper:
 *   1. Inline:   "10 + 30 + 40"
 *   2. Stacked:  "100" / "+ 25" / "+ 45" / "- 20"  (a vertical sum)
 *
 * Multiple independent problems may be separated by an "(or)" marker or by
 * a blank line; each is returned as its own result.
 */
object MathParser {

    data class Result(
        val expression: String,
        val value: Double?,
        val error: String? = null
    )

    /** Normalize the many glyphs OCR returns for math symbols. */
    private fun normalizeSymbols(raw: String): String {
        val sb = StringBuilder()
        for (c in raw) {
            val mapped = when (c) {
                '×', 'x', 'X', '✕', '∗', '·', '•' -> '*'
                '÷', '∕', '⁄' -> '/'
                '−', '–', '—', '－' -> '-'   // unicode minus / dashes
                '＋' -> '+'
                ',' -> ' '                    // thousands separators -> drop
                else -> c
            }
            sb.append(mapped)
        }
        return sb.toString()
    }

    /** Keep only characters that are valid inside an arithmetic expression. */
    private fun sanitizeExpression(line: String): String {
        return line.filter { it.isDigit() || it in "+-*/%^(). " }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Group raw OCR lines into logical problems and stitch stacked sums into a
     * single inline expression. A line that begins with an operator is treated
     * as a continuation of the running expression above it.
     */
    private fun buildExpressions(text: String): List<String> {
        val problems = mutableListOf<String>()
        var current = StringBuilder()

        fun flush() {
            val expr = sanitizeExpression(current.toString())
            if (expr.any { it.isDigit() }) problems.add(expr)
            current = StringBuilder()
        }

        for (rawLine in normalizeSymbols(text).lines()) {
            val line = rawLine.trim()
            // Separators between independent problems.
            if (line.isEmpty() || line.replace(Regex("[()\\s]"), "")
                    .equals("or", ignoreCase = true)
            ) {
                flush()
                continue
            }
            val cleaned = sanitizeExpression(line)
            if (cleaned.isEmpty()) continue

            val startsWithOperator = cleaned.first() in "+-*/%^"
            if (current.isEmpty() || startsWithOperator) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(cleaned)
            } else {
                // A new number-led line with no operator => new problem.
                flush()
                current.append(cleaned)
            }
        }
        flush()
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
