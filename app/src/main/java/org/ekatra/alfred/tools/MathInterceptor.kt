package org.ekatra.alfred.tools

import org.ekatra.alfred.persona.ActivePersona
import org.ekatra.alfred.persona.Persona
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * MathInterceptor — deterministic calculator for student math questions.
 *
 * Pattern-matches arithmetic, percentages, square roots, and powers in
 * natural-language input and returns the **correct numeric result**.
 *
 * This runs *before* the LLM so the model can explain around a verified
 * answer instead of hallucinating wrong arithmetic (a known weakness of
 * tiny 0.5B models).
 *
 * Stateless, no dependencies — safe to create as a singleton via Hilt.
 */
class MathInterceptor(private val persona: Persona = ActivePersona) {

    /**
     * If [input] contains a recognisable arithmetic expression, evaluate it
     * and return an augmented prompt string that includes the correct answer.
     * Otherwise return `null` (the message should go to the LLM as-is).
     */
    fun intercept(input: String): InterceptResult? {
        val result = tryEvaluateMath(input) ?: return null
        val augmented = persona.mathExplanationPrompt(input, result)
        return InterceptResult(numericAnswer = result, augmentedPrompt = augmented)
    }

    data class InterceptResult(
        val numericAnswer: String,
        val augmentedPrompt: String
    )

    // ==================== Evaluation ====================

    /**
     * Tries to extract and evaluate a simple arithmetic expression.
     * Returns the numeric result as a formatted String, or null.
     *
     * Handles:
     *   - "what is 247 ÷ 13", "calculate 15 × 23", "12 + 5 - 3"
     *   - Unicode operators (×, ÷) and words (plus, minus, times, divided by)
     *   - Percentages ("what is 15% of 200")
     *   - Square roots ("square root of 144", "sqrt 144")
     *   - Powers ("2 to the power of 10", "2^10")
     */
    internal fun tryEvaluateMath(input: String): String? {
        val text = input.trim().lowercase()

        // Percentage: "what is X% of Y"
        val pctRegex = Regex("""(\d+(?:\.\d+)?)\s*%\s*(?:of|من)\s*(\d+(?:\.\d+)?)""")
        pctRegex.find(text)?.let { match ->
            val pct = match.groupValues[1].toDouble()
            val base = match.groupValues[2].toDouble()
            return formatNumber(pct / 100.0 * base)
        }

        // Square root: "square root of 144", "sqrt 144", "√144"
        val sqrtRegex = Regex("""(?:square\s*root\s*(?:of\s*)?|sqrt\s*|جذر\s*تربيعي\s*(?:لـ|ل|من)?\s*|√\s*)(\d+(?:\.\d+)?)""")
        sqrtRegex.find(text)?.let { match ->
            val n = match.groupValues[1].toDouble()
            return formatNumber(sqrt(n))
        }

        // Power: "2 to the power of 10", "2^10", "2 power 10"
        val powRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:\^|\*\*|to\s+the\s+power\s+(?:of\s+)?|power\s*|أس\s*)(\d+(?:\.\d+)?)""")
        powRegex.find(text)?.let { match ->
            val base = match.groupValues[1].toDouble()
            val exp = match.groupValues[2].toDouble()
            return formatNumber(base.pow(exp))
        }

        // General arithmetic: extract something that looks like "A op B (op C ...)"
        var expr = text
            .replace("×", "*").replace("÷", "/")
            .replace("plus", "+").replace("minus", "-")
            .replace("times", "*").replace("multiplied by", "*")
            .replace("divided by", "/").replace("over", "/")
            .replace("زائد", "+").replace("ناقص", "-")
            .replace("ضرب", "*").replace("قسمة", "/")

        // Strip surrounding words: "what is ...", "calculate ..."
        expr = expr.replace(Regex("""^.*?([\d(])"""), "$1")
        expr = expr.replace(Regex("""[^0-9+\-*/().^ ]+$"""), "")
        expr = expr.trim()

        // Must contain at least one operator and one digit
        if (!expr.matches(Regex("""[\d\s+\-*/().]+"""))) return null
        if (!expr.contains(Regex("""[+\-*/]"""))) return null
        if (!expr.contains(Regex("""\d"""))) return null

        return try {
            val result = evalSimpleExpr(expr)
            if (result.isNaN() || result.isInfinite()) null
            else formatNumber(result)
        } catch (_: Exception) {
            null
        }
    }

    /** Simple recursive-descent evaluator for +, -, *, / with parentheses. */
    internal fun evalSimpleExpr(expr: String): Double {
        val tokens = expr.replace(" ", "")
        // Use an anonymous object so member functions can call each other
        // regardless of declaration order (avoids local-function forward-ref).
        val parser = object {
            var pos = 0

            fun parseNumber(): Double {
                val start = pos
                if (pos < tokens.length && tokens[pos] == '-') pos++
                while (pos < tokens.length && (tokens[pos].isDigit() || tokens[pos] == '.')) pos++
                return tokens.substring(start, pos).toDouble()
            }

            fun parseFactor(): Double {
                return if (pos < tokens.length && tokens[pos] == '(') {
                    pos++ // skip '('
                    val v = parseExpr()
                    if (pos < tokens.length && tokens[pos] == ')') pos++ // skip ')'
                    v
                } else {
                    parseNumber()
                }
            }

            fun parseTerm(): Double {
                var left = parseFactor()
                while (pos < tokens.length && tokens[pos] in listOf('*', '/')) {
                    val op = tokens[pos++]
                    val right = parseFactor()
                    left = if (op == '*') left * right else left / right
                }
                return left
            }

            fun parseExpr(): Double {
                var left = parseTerm()
                while (pos < tokens.length && tokens[pos] in listOf('+', '-')) {
                    val op = tokens[pos++]
                    val right = parseTerm()
                    left = if (op == '+') left + right else left - right
                }
                return left
            }
        }
        return parser.parseExpr()
    }

    internal fun formatNumber(d: Double): String {
        return if (d == d.toLong().toDouble()) d.toLong().toString()
        else "%.4f".format(d).trimEnd('0').trimEnd('.')
    }
}
