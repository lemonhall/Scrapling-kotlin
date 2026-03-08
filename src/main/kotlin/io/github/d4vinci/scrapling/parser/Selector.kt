package io.github.d4vinci.scrapling.parser

import io.github.d4vinci.scrapling.core.AttributesHandler
import io.github.d4vinci.scrapling.core.TextHandler
import io.github.d4vinci.scrapling.core.TextHandlers
import io.github.d4vinci.scrapling.core.normalizeWhitespace
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private sealed interface SelectionNode

private data class ElementNode(
    val element: Element,
) : SelectionNode

private data class ValueNode(
    val value: String,
    val owner: Element?,
) : SelectionNode

class Selector private constructor(
    private val node: SelectionNode,
    val url: String = "",
) {
    constructor(content: String, url: String = "") : this(ElementNode(Jsoup.parse(content)), url)

    val tag: String
        get() = when (node) {
            is ElementNode -> node.element.tagName()
            is ValueNode -> "#text"
        }

    val text: TextHandler
        get() = when (node) {
            is ElementNode -> TextHandler(
                node.element.textNodes()
                    .map { it.text().normalizeWhitespace() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                    .normalizeWhitespace(),
            )
            is ValueNode -> TextHandler(node.value.normalizeWhitespace())
        }

    val attrib: AttributesHandler
        get() = when (node) {
            is ElementNode -> AttributesHandler.from(node.element.attributes())
            is ValueNode -> node.owner?.let { AttributesHandler.from(it.attributes()) } ?: AttributesHandler.empty()
        }

    val htmlContent: String
        get() = when (node) {
            is ElementNode -> node.element.outerHtml().trim()
            is ValueNode -> node.value
        }

    val body: String
        get() = when (node) {
            is ElementNode -> node.element.outerHtml()
            is ValueNode -> ""
        }

    fun getAllText(): TextHandler = when (node) {
        is ElementNode -> TextHandler(node.element.text().normalizeWhitespace())
        is ValueNode -> TextHandler(node.value.normalizeWhitespace())
    }

    fun prettify(): TextHandler = TextHandler(
        when (node) {
            is ElementNode -> prettifyElement(node.element)
            is ValueNode -> node.value
        },
    )

    fun parent(): Selector? = when (node) {
        is ElementNode -> node.element.parent()?.let(::wrapElement)
        is ValueNode -> node.owner?.let(::wrapElement)
    }

    fun children(): Selectors = when (node) {
        is ElementNode -> Selectors(node.element.children().map(::wrapElement))
        is ValueNode -> Selectors(emptyList())
    }

    fun siblings(): Selectors = when (node) {
        is ElementNode -> Selectors(node.element.siblingElements().map(::wrapElement))
        is ValueNode -> Selectors(emptyList())
    }

    fun next(): Selector? = when (node) {
        is ElementNode -> node.element.nextElementSibling()?.let(::wrapElement)
        is ValueNode -> null
    }

    fun previous(): Selector? = when (node) {
        is ElementNode -> node.element.previousElementSibling()?.let(::wrapElement)
        is ValueNode -> null
    }

    fun css(selector: String): Selectors = when (node) {
        is ElementNode -> selectWithPseudo(node.element, selector)
        is ValueNode -> Selectors(emptyList())
    }

    fun find(selector: String): Selector? = css(selector).first()

    fun findAll(selector: String): Selectors = css(selector)

    fun get(): TextHandler = TextHandler(htmlContent)

    fun getall(): TextHandlers = TextHandlers(listOf(get()))

    private fun wrapElement(element: Element): Selector = Selector(ElementNode(element), url)

    private fun wrapValue(value: String, owner: Element?): Selector = Selector(ValueNode(value, owner), url)

    private fun selectWithPseudo(root: Element, selector: String): Selectors {
        val trimmed = selector.trim()

        if (trimmed.endsWith("::text")) {
            val baseSelector = trimmed.removeSuffix("::text").trim()
            val baseElements = if (baseSelector.isEmpty()) listOf(root) else root.select(baseSelector)

            return Selectors(
                baseElements.flatMap { base ->
                    base.textNodes()
                        .map { it.text().normalizeWhitespace() }
                        .filter { it.isNotEmpty() }
                        .map { value -> wrapValue(value, base) }
                },
            )
        }

        val attrMatch = ATTR_PSEUDO_REGEX.matchEntire(trimmed)
        if (attrMatch != null) {
            val baseSelector = attrMatch.groupValues[1].trim()
            val attributeName = attrMatch.groupValues[2].trim()
            val baseElements = if (baseSelector.isEmpty()) listOf(root) else root.select(baseSelector)

            return Selectors(
                baseElements.mapNotNull { base ->
                    base.takeIf { it.hasAttr(attributeName) }?.attr(attributeName)?.let { value -> wrapValue(value, base) }
                },
            )
        }

        return Selectors(root.select(trimmed).map(::wrapElement))
    }

    private fun prettifyElement(element: Element): String {
        val parsed = Jsoup.parse(element.outerHtml())
        parsed.outputSettings().prettyPrint(true).indentAmount(2)

        return if (element is Document) {
            parsed.outerHtml().trim()
        } else {
            parsed.body().children().firstOrNull()?.outerHtml()?.trim().orEmpty()
        }
    }

    companion object {
        private val ATTR_PSEUDO_REGEX = Regex("^(.*)::attr\\(([^)]+)\\)$")
    }
}

class Selectors internal constructor(
    private val values: List<Selector>,
) : List<Selector> by values {
    val length: Int
        get() = values.size

    fun css(selector: String): Selectors = Selectors(values.flatMap { it.css(selector) })

    fun get(default: TextHandler? = null): TextHandler? = values.firstOrNull()?.get() ?: default

    fun getall(): TextHandlers = TextHandlers(values.map { it.get() })

    fun first(): Selector? = values.firstOrNull()

    fun last(): Selector? = values.lastOrNull()
}
