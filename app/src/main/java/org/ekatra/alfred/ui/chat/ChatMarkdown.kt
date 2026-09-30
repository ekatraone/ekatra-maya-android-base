package org.ekatra.alfred.ui.chat


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection

private val MATH_SNIPPET_REGEX =
    Regex("""(\d+\s*/\s*\d+|\d+(?:\s*[+\-×x*/=]\s*\d+)+)""")

private fun stabilizeMathDirection(text: String): String {
    return MATH_SNIPPET_REGEX.replace(text) { match ->
        // Keep equation/fraction token order stable inside Arabic sentences.
        "⁦${match.value}⁩"
    }
}

internal fun containsArabicScript(text: String): Boolean {
    return text.any { it in '؀'..'ۿ' }
}

// Simple markdown renderer — handles **bold**, *italic*, `code`, and - bullet lists
@Composable
fun SimpleMarkdown(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val normalizedText = remember(text) { stabilizeMathDirection(text) }
    val hasArabic = remember(normalizedText) { containsArabicScript(normalizedText) }
    val lines = remember(normalizedText) { normalizedText.split("\n") }
    val layoutDirection = if (hasArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    val textDirection = if (hasArabic) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    Column(modifier = modifier) {
        lines.forEach { line ->
            val isBullet = line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")
            val content = if (isBullet) line.trimStart().removePrefix("- ").removePrefix("* ") else line
            val annotated = buildAnnotatedString {
                var i = 0
                while (i < content.length) {
                    when {
                        content.startsWith("**", i) -> {
                            val end = content.indexOf("**", i + 2)
                            if (end != -1) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(content.substring(i + 2, end))
                                }
                                i = end + 2
                            } else { append(content[i]); i++ }
                        }
                        content.startsWith("*", i) && !content.startsWith("**", i) -> {
                            val end = content.indexOf("*", i + 1)
                            if (end != -1) {
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(content.substring(i + 1, end))
                                }
                                i = end + 1
                            } else { append(content[i]); i++ }
                        }
                        content.startsWith("`", i) -> {
                            val end = content.indexOf("`", i + 1)
                            if (end != -1) {
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFE2E8F0))) {
                                    append(content.substring(i + 1, end))
                                }
                                i = end + 1
                            } else { append(content[i]); i++ }
                        }
                        else -> { append(content[i]); i++ }
                    }
                }
            }
            Row {
                if (isBullet) Text("• ", color = color, fontSize = fontSize)
                Text(
                    text = annotated,
                    color = color,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.5f,
                    textAlign = TextAlign.Start,
                    style = LocalTextStyle.current.copy(textDirection = textDirection)
                )
            }
        }
    }
    }
}

// Subject quick-start chips

// ==================== Main Chat Screen ====================
