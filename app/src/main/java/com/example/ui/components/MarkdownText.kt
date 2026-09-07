package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownNode {
    data class Header(val level: Int, val text: String) : MarkdownNode()
    data class Paragraph(val text: String) : MarkdownNode()
    data class BulletItem(val text: String) : MarkdownNode()
    data class NumberedItem(val number: String, val text: String) : MarkdownNode()
    data class CodeBlock(val language: String, val code: String) : MarkdownNode()
}

fun parseMarkdown(content: String): List<MarkdownNode> {
    val nodes = mutableListOf<MarkdownNode>()
    val lines = content.lines()
    var i = 0

    while (i < lines.size) {
        val rawLine = lines[i]
        val trimmed = rawLine.trim()

        // Check for code block opening
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) {
                i++ // consume closing ```
            }
            nodes.add(MarkdownNode.CodeBlock(language, codeLines.joinToString("\n")))
            continue
        }

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Headers
        when {
            trimmed.startsWith("#### ") -> {
                nodes.add(MarkdownNode.Header(4, trimmed.removePrefix("#### ").trim()))
                i++
            }
            trimmed.startsWith("### ") -> {
                nodes.add(MarkdownNode.Header(3, trimmed.removePrefix("### ").trim()))
                i++
            }
            trimmed.startsWith("## ") -> {
                nodes.add(MarkdownNode.Header(2, trimmed.removePrefix("## ").trim()))
                i++
            }
            trimmed.startsWith("# ") -> {
                nodes.add(MarkdownNode.Header(1, trimmed.removePrefix("# ").trim()))
                i++
            }
            // Bullet items
            trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ") -> {
                val bulletContent = trimmed.substring(2).trim()
                nodes.add(MarkdownNode.BulletItem(bulletContent))
                i++
            }
            // Numbered items
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val dotIndex = trimmed.indexOf('.')
                val number = trimmed.substring(0, dotIndex).trim()
                val itemText = trimmed.substring(dotIndex + 1).trim()
                nodes.add(MarkdownNode.NumberedItem(number, itemText))
                i++
            }
            else -> {
                // Collect contiguous non-empty lines into a single paragraph
                val paragraphLines = mutableListOf<String>()
                while (i < lines.size) {
                    val nextLine = lines[i].trim()
                    if (nextLine.isEmpty() ||
                        nextLine.startsWith("```") ||
                        nextLine.startsWith("#") ||
                        nextLine.startsWith("* ") ||
                        nextLine.startsWith("- ") ||
                        nextLine.startsWith("• ") ||
                        nextLine.matches(Regex("^\\d+\\.\\s+.*"))
                    ) {
                        break
                    }
                    paragraphLines.add(lines[i])
                    i++
                }
                if (paragraphLines.isNotEmpty()) {
                    nodes.add(MarkdownNode.Paragraph(paragraphLines.joinToString(" ")))
                } else {
                    i++
                }
            }
        }
    }

    return nodes
}

@Composable
fun buildFormattedInlineText(text: String, baseColor: Color): AnnotatedString {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            // Check for bold **...**
            if (text.startsWith("**", cursor)) {
                val end = text.indexOf("**", cursor + 2)
                if (end != -1) {
                    val boldText = text.substring(cursor + 2, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                    append(boldText)
                    pop()
                    cursor = end + 2
                    continue
                }
            }
            // Check for inline code `...`
            if (text.startsWith("`", cursor)) {
                val end = text.indexOf("`", cursor + 1)
                if (end != -1) {
                    val codeText = text.substring(cursor + 1, end)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBg,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = baseColor
                        )
                    )
                    append(" $codeText ")
                    pop()
                    cursor = end + 1
                    continue
                }
            }
            // Check for italic *...*
            if (text.startsWith("*", cursor)) {
                val end = text.indexOf("*", cursor + 1)
                if (end != -1) {
                    val italicText = text.substring(cursor + 1, end)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                    append(italicText)
                    pop()
                    cursor = end + 1
                    continue
                }
            }
            // Normal character
            append(text[cursor])
            cursor++
        }
    }
}

@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onBackground
) {
    val nodes = parseMarkdown(content)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        nodes.forEach { node ->
            when (node) {
                is MarkdownNode.Header -> {
                    val (style, topPad) = when (node.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            lineHeight = 26.sp
                        ) to 6.dp
                        2 -> MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            lineHeight = 22.sp
                        ) to 4.dp
                        3 -> MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        ) to 2.dp
                        else -> MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ) to 2.dp
                    }
                    Text(
                        text = buildFormattedInlineText(node.text, textColor),
                        style = style,
                        color = textColor,
                        modifier = Modifier.padding(top = topPad)
                    )
                }

                is MarkdownNode.Paragraph -> {
                    Text(
                        text = buildFormattedInlineText(node.text, textColor),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 23.sp
                        ),
                        color = textColor
                    )
                }

                is MarkdownNode.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(textColor.copy(alpha = 0.7f))
                        )
                        Text(
                            text = buildFormattedInlineText(node.text, textColor),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownNode.NumberedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${node.number}.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            color = textColor
                        )
                        Text(
                            text = buildFormattedInlineText(node.text, textColor),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownNode.CodeBlock -> {
                    CodeBlockView(
                        code = node.code,
                        language = node.language,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
