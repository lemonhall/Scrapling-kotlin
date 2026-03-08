package io.github.d4vinci.scrapling.parser

import io.github.d4vinci.scrapling.core.AttributesHandler
import io.github.d4vinci.scrapling.core.TextHandler
import io.github.d4vinci.scrapling.core.normalizeWhitespace
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Selector private constructor(
    private val element: Element,
    val url: String = "",
) {
    constructor(content: String, url: String = "") : this(Jsoup.parse(content), url)

    val tag: String
        get() = element.tagName()

    val text: TextHandler
        get() = TextHandler(
            element.textNodes()
                .map { it.text().normalizeWhitespace() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .normalizeWhitespace(),
        )

    val attrib: AttributesHandler
        get() = AttributesHandler.from(element.attributes())

    val htmlContent: String
        get() = element.html().trim()

    val body: String
        get() = element.outerHtml()

    fun getAllText(): TextHandler = TextHandler(element.text().normalizeWhitespace())

    fun prettify(): String = body

    fun parent(): Selector? = element.parent()?.let(::wrap)

    fun children(): Selectors = Selectors(element.children().map(::wrap))

    fun siblings(): Selectors = Selectors(element.siblingElements().map(::wrap))

    fun next(): Selector? = element.nextElementSibling()?.let(::wrap)

    fun previous(): Selector? = element.previousElementSibling()?.let(::wrap)

    fun css(selector: String): Selectors = Selectors(element.select(selector).map(::wrap))

    fun find(selector: String): Selector? = css(selector).first()

    fun findAll(selector: String): Selectors = css(selector)

    private fun wrap(node: Element): Selector = Selector(node, url)
}

class Selectors internal constructor(
    private val values: List<Selector>,
) : List<Selector> by values {
    val length: Int
        get() = values.size

    fun css(selector: String): Selectors = Selectors(values.flatMap { it.css(selector) })

    fun first(): Selector? = values.firstOrNull()

    fun last(): Selector? = values.lastOrNull()
}

