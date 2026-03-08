package io.github.d4vinci.scrapling.core.shell

import io.github.d4vinci.scrapling.fetchers.static.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

enum class ExtractionType(
    val value: String,
) {
    MARKDOWN("markdown"),
    HTML("html"),
    TEXT("text"),
    ;

    companion object {
        fun fromValue(value: String): ExtractionType = entries.firstOrNull { entry -> entry.value == value.lowercase() }
            ?: error("Unknown extraction type: $value")

        fun fromPath(path: Path): ExtractionType = when (path.fileName.toString().substringAfterLast('.', missingDelimiterValue = "")) {
            "md" -> MARKDOWN
            "html" -> HTML
            "txt" -> TEXT
            else -> error("Unknown file type: filename must end with '.md', '.html', or '.txt'")
        }
    }
}

object ContentExtractor {
    private val noiseTags = setOf("script", "style", "noscript", "svg", "iframe")

    fun extract(
        response: Response,
        extractionType: ExtractionType = ExtractionType.MARKDOWN,
        cssSelector: String? = null,
        mainContentOnly: Boolean = false,
    ): List<String> {
        val document = Jsoup.parse(response.content.toString(Charsets.UTF_8), response.url)
        val baseElement = if (mainContentOnly) document.body()?.clone() ?: document.clone() else document.clone()
        stripNoise(baseElement)
        val elements = if (cssSelector.isNullOrBlank()) listOf(baseElement) else baseElement.select(cssSelector).toList()
        return elements.map { element ->
            when (extractionType) {
                ExtractionType.MARKDOWN -> HtmlMarkdownRenderer.render(element)
                ExtractionType.HTML -> element.outerHtml().trim()
                ExtractionType.TEXT -> normalizeText(element.text())
            }
        }
    }

    fun writeContentToFile(
        response: Response,
        filename: String,
        cssSelector: String? = null,
        mainContentOnly: Boolean = false,
    ): Path {
        require(filename.isNotBlank()) { "Filename must be provided" }
        val path = Path.of(filename)
        val extractionType = ExtractionType.fromPath(path)
        path.parent?.createDirectories()
        val content = extract(response, extractionType, cssSelector, mainContentOnly).joinToString(separator = System.lineSeparator())
        path.writeText(content, Charsets.UTF_8)
        return path
    }

    private fun stripNoise(element: Element) {
        noiseTags.forEach { tag -> element.select(tag).remove() }
    }

    private fun normalizeText(value: String): String = value.replace(Regex("\\s+"), " ").trim()
}

private object HtmlMarkdownRenderer {
    fun render(element: Element): String {
        val builder = StringBuilder()
        renderNode(element, builder)
        return builder.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun renderNode(node: Node, builder: StringBuilder) {
        when (node) {
            is TextNode -> appendText(builder, node.text())
            is Element -> renderElement(node, builder)
        }
    }

    private fun renderElement(element: Element, builder: StringBuilder) {
        when (element.tagName().lowercase()) {
            "html", "body", "main", "article", "section", "div" -> renderChildren(element, builder)
            "h1" -> appendHeading(builder, element.text().trim(), '=')
            "h2" -> appendHeading(builder, element.text().trim(), '-')
            "h3", "h4", "h5", "h6" -> {
                builder.append("#".repeat(element.tagName().drop(1).toInt())).append(' ')
                builder.append(element.text().trim()).append("\n\n")
            }
            "p" -> {
                renderChildren(element, builder)
                builder.append("\n\n")
            }
            "ul", "ol" -> {
                renderChildren(element, builder)
                builder.append("\n")
            }
            "li" -> {
                builder.append("- ")
                renderChildren(element, builder)
                builder.append("\n")
            }
            "a" -> {
                val text = element.text().trim()
                val href = element.absUrl("href").ifBlank { element.attr("href") }
                if (href.isBlank()) {
                    builder.append(text)
                } else {
                    builder.append("[").append(text).append("](").append(href).append(")")
                }
            }
            "strong", "b" -> {
                builder.append("**")
                renderChildren(element, builder)
                builder.append("**")
            }
            "em", "i" -> {
                builder.append('_')
                renderChildren(element, builder)
                builder.append('_')
            }
            "br" -> builder.append("\n")
            else -> renderChildren(element, builder)
        }
    }

    private fun renderChildren(element: Element, builder: StringBuilder) {
        element.childNodes().forEach { child -> renderNode(child, builder) }
    }

    private fun appendHeading(builder: StringBuilder, text: String, underline: Char) {
        if (text.isBlank()) return
        builder.append(text).append("\n")
        builder.append(underline.toString().repeat(text.length)).append("\n\n")
    }

    private fun appendText(builder: StringBuilder, text: String) {
        val normalized = text.replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return
        if (builder.isNotEmpty() && builder.last().isLetterOrDigit()) {
            builder.append(' ')
        }
        builder.append(normalized.trim())
    }
}
