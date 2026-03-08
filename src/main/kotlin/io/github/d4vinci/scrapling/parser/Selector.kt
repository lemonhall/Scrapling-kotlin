package io.github.d4vinci.scrapling.parser

import io.github.d4vinci.scrapling.core.AttributesHandler
import io.github.d4vinci.scrapling.core.TextHandler
import io.github.d4vinci.scrapling.core.TextHandlers
import io.github.d4vinci.scrapling.core.normalizeWhitespace
import io.github.d4vinci.scrapling.core.storage.ElementSnapshot
import io.github.d4vinci.scrapling.core.storage.SQLiteStorageSystem
import io.github.d4vinci.scrapling.core.storage.StorageSystem
import io.github.d4vinci.scrapling.core.storage.StorageTools
import javax.xml.namespace.QName
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

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
    internal val isAdaptiveEnabled: Boolean = false,
    internal val storageSystem: StorageSystem? = null,
) {
    constructor(
        content: String,
        url: String = "",
        adaptive: Boolean = false,
        storageSystem: StorageSystem? = null,
    ) : this(
        node = ElementNode(Jsoup.parse(content)),
        url = url,
        isAdaptiveEnabled = adaptive,
        storageSystem = when {
            !adaptive -> storageSystem
            storageSystem != null -> storageSystem
            else -> SQLiteStorageSystem.default(url)
        },
    )

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

    fun hasClass(className: String): Boolean = when (node) {
        is ElementNode -> node.element.classNames().contains(className)
        is ValueNode -> false
    }

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

    fun css(
        selector: String,
        identifier: String = "",
        adaptive: Boolean = false,
        autoSave: Boolean = false,
        percentage: Int = 0,
    ): Selectors = when (node) {
        is ElementNode -> selectCss(node.element, selector, identifier, adaptive, autoSave, percentage)
        is ValueNode -> Selectors(emptyList())
    }

    fun xpath(
        selector: String,
        identifier: String = "",
        adaptive: Boolean = false,
        autoSave: Boolean = false,
        percentage: Int = 0,
        variables: Map<String, Any?> = emptyMap(),
    ): Selectors = when (node) {
        is ElementNode -> selectXpath(node.element, selector, identifier, adaptive, autoSave, percentage, variables)
        is ValueNode -> Selectors(emptyList())
    }

    fun find(selector: String): Selector? = css(selector).first()

    fun findAll(selector: String): Selectors = css(selector)

    fun get(): TextHandler = TextHandler(htmlContent)

    fun getall(): TextHandlers = TextHandlers(listOf(get()))

    fun save(element: Selector, identifier: String) {
        val target = when (val targetNode = element.node) {
            is ElementNode -> targetNode.element
            is ValueNode -> targetNode.owner ?: return
        }
        ensureAdaptiveEnabled().save(StorageTools.elementToSnapshot(target), identifier)
    }

    fun retrieve(identifier: String): ElementSnapshot? = ensureAdaptiveEnabled().retrieve(identifier)

    fun relocate(snapshot: ElementSnapshot, percentage: Int = 0): Selectors {
        val rootElement = elementOrNull() ?: return Selectors(emptyList())
        val candidates = rootElement.select("*").ifEmpty { listOf(rootElement) }
        val scoreTable = candidates.groupBy { similarity(snapshot, it) }
        val highest = scoreTable.keys.maxOrNull() ?: return Selectors(emptyList())
        if (highest < percentage) return Selectors(emptyList())
        return Selectors(scoreTable[highest].orEmpty().map(::wrapElement))
    }

    private fun ensureAdaptiveEnabled(): StorageSystem {
        check(isAdaptiveEnabled && storageSystem != null) {
            "Can't use adaptive features while adaptive is disabled."
        }
        return storageSystem
    }

    private fun selectCss(
        root: Element,
        selector: String,
        identifier: String,
        adaptive: Boolean,
        autoSave: Boolean,
        percentage: Int,
    ): Selectors {
        val selectorParts = selector.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (selectorParts.size > 1) {
            return Selectors(selectorParts.flatMap { part -> selectCss(root, part, identifier.ifBlank { part }, adaptive, autoSave, percentage) })
        }

        val trimmed = selector.trim()
        val directResults = when {
            trimmed.endsWith("::text") -> {
                val baseSelector = trimmed.removeSuffix("::text").trim()
                val baseElements = if (baseSelector.isEmpty()) listOf(root) else root.select(baseSelector)
                baseElements.flatMap { base ->
                    base.textNodes()
                        .map { it.text().normalizeWhitespace() }
                        .filter { it.isNotEmpty() }
                        .map { value -> wrapValue(value, base) }
                }
            }

            ATTR_PSEUDO_REGEX.matches(trimmed) -> {
                val match = ATTR_PSEUDO_REGEX.matchEntire(trimmed)!!
                val baseSelector = match.groupValues[1].trim()
                val attributeName = match.groupValues[2].trim()
                val baseElements = if (baseSelector.isEmpty()) listOf(root) else root.select(baseSelector)
                baseElements.mapNotNull { base ->
                    base.takeIf { it.hasAttr(attributeName) }?.attr(attributeName)?.let { wrapValue(it, base) }
                }
            }

            else -> root.select(trimmed).map(::wrapElement)
        }

        if (directResults.isNotEmpty()) {
            if (autoSave && isAdaptiveEnabled) {
                save(directResults.first(), identifier.ifBlank { selector })
            }
            return Selectors(directResults)
        }

        if (adaptive && isAdaptiveEnabled) {
            val snapshot = retrieve(identifier.ifBlank { selector }) ?: return Selectors(emptyList())
            return relocate(snapshot, percentage)
        }

        return Selectors(emptyList())
    }

    private fun selectXpath(
        root: Element,
        selector: String,
        identifier: String,
        adaptive: Boolean,
        autoSave: Boolean,
        percentage: Int,
        variables: Map<String, Any?>,
    ): Selectors {
        val document = Jsoup.parse(root.outerHtml())
        val w3cDocument = W3CDom().fromJsoup(document)
        val resolvedSelector = resolveXpathVariables(selector, variables)
        val expression = XPathFactory.newInstance().newXPath().compile(resolvedSelector)
        val nodeList = expression.evaluate(w3cDocument, XPathConstants.NODESET) as NodeList
        val engineResults = buildList {
            for (index in 0 until nodeList.length) {
                val found = nodeList.item(index)
                when (found.nodeType) {
                    Node.ELEMENT_NODE -> add(wrapElement(parseNodeAsElement(found)))
                    Node.TEXT_NODE, Node.ATTRIBUTE_NODE -> add(wrapValue(found.nodeValue ?: "", null))
                }
            }
        }
        val results = if (engineResults.isNotEmpty()) engineResults else fallbackXpath(root, resolvedSelector)

        if (results.isNotEmpty()) {
            if (autoSave && isAdaptiveEnabled) {
                save(results.first(), identifier.ifBlank { selector })
            }
            return Selectors(results)
        }

        if (adaptive && isAdaptiveEnabled) {
            val snapshot = retrieve(identifier.ifBlank { selector }) ?: return Selectors(emptyList())
            return relocate(snapshot, percentage)
        }

        return Selectors(emptyList())
    }

    private fun parseNodeAsElement(node: Node): Element {
        val html = nodeToString(node)
        val parsed = Jsoup.parseBodyFragment(html)
        return parsed.body().children().first()
    }

    private fun nodeToString(node: Node): String {
        val writer = java.io.StringWriter()
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        }
        transformer.transform(DOMSource(node), StreamResult(writer))
        return writer.toString()
    }

    private fun fallbackXpath(root: Element, selector: String): List<Selector> {
        SIMPLE_TEXT_XPATH.matchEntire(selector)?.let { match ->
            val tag = match.groupValues[1]
            val expectedText = match.groupValues[2]
            return root.select(tag).filter { it.text().normalizeWhitespace() == expectedText.normalizeWhitespace() }.map(::wrapElement)
        }

        SIMPLE_TAG_XPATH.matchEntire(selector)?.let { match ->
            val tag = match.groupValues[1]
            return root.select(tag).map(::wrapElement)
        }

        return emptyList()
    }

    private fun resolveXpathVariables(selector: String, variables: Map<String, Any?>): String =
        variables.entries.fold(selector) { current, (key, value) ->
            current.replace("$$key", xpathLiteral(value?.toString().orEmpty()))
        }

    private fun xpathLiteral(value: String): String {
        if (!value.contains('\'')) return "'$value'"
        if (!value.contains('"')) return "\"$value\""
        val parts = value.split('\'')
        return parts.joinToString(
            separator = ", \"'\", ",
            prefix = "concat(",
            postfix = ")",
        ) { "'$it'" }
    }

    private fun elementOrNull(): Element? = when (node) {
        is ElementNode -> node.element
        is ValueNode -> node.owner
    }

    private fun wrapElement(element: Element): Selector =
        Selector(ElementNode(element), url, isAdaptiveEnabled, storageSystem)

    private fun wrapValue(value: String, owner: Element?): Selector =
        Selector(ValueNode(value, owner), url, isAdaptiveEnabled, storageSystem)

    private fun prettifyElement(element: Element): String {
        val parsed = Jsoup.parse(element.outerHtml())
        parsed.outputSettings().prettyPrint(true).indentAmount(2)

        return if (element is Document) {
            parsed.outerHtml().trim()
        } else {
            parsed.body().children().firstOrNull()?.outerHtml()?.trim().orEmpty()
        }
    }

    private fun similarity(original: ElementSnapshot, candidate: Element): Int {
        val data = StorageTools.elementToSnapshot(candidate)
        var score = 0.0
        var checks = 0

        score += if (original.tag == data.tag) 1.0 else 0.0
        checks += 1

        original.allText?.let {
            score += stringSimilarity(it, data.allText.orEmpty())
            checks += 1
        }

        score += mapSimilarity(original.attributes, data.attributes)
        checks += 1

        listOf("class", "id", "href", "src").forEach { attributeName ->
            original.attributes[attributeName]?.let { left ->
                score += stringSimilarity(left, data.attributes[attributeName].orEmpty())
                checks += 1
            }
        }

        score += listSimilarity(original.path, data.path)
        checks += 1

        original.parentName?.let {
            score += stringSimilarity(it, data.parentName.orEmpty())
            checks += 1
            score += mapSimilarity(original.parentAttributes, data.parentAttributes)
            checks += 1
            original.parentText?.let { text ->
                score += stringSimilarity(text, data.parentText.orEmpty())
                checks += 1
            }
        }

        if (original.siblings.isNotEmpty()) {
            score += listSimilarity(original.siblings, data.siblings)
            checks += 1
        }
        if (original.children.isNotEmpty()) {
            score += listSimilarity(original.children, data.children)
            checks += 1
        }

        return if (checks == 0) 0 else ((score / checks) * 100).toInt()
    }

    companion object {
        private val ATTR_PSEUDO_REGEX = Regex("^(.*)::attr\\(([^)]+)\\)$")
        private val SIMPLE_TAG_XPATH = Regex("^//([a-zA-Z0-9_-]+)$")
        private val SIMPLE_TEXT_XPATH = Regex("^//([a-zA-Z0-9_-]+)\\[text\\(\\)=['\"](.*)['\"]\\]$")

        private fun stringSimilarity(left: String, right: String): Double {
            if (left == right) return 1.0
            if (left.isEmpty() && right.isEmpty()) return 1.0
            if (left.isEmpty() || right.isEmpty()) return 0.0

            val distance = levenshtein(left, right)
            return 1.0 - distance.toDouble() / maxOf(left.length, right.length).toDouble()
        }

        private fun mapSimilarity(left: Map<String, String>, right: Map<String, String>): Double {
            if (left.isEmpty() && right.isEmpty()) return 1.0
            val keys = left.keys + right.keys
            if (keys.isEmpty()) return 1.0
            return keys.map { key -> stringSimilarity(left[key].orEmpty(), right[key].orEmpty()) }.average()
        }

        private fun listSimilarity(left: List<String>, right: List<String>): Double =
            stringSimilarity(left.joinToString("/"), right.joinToString("/"))

        private fun levenshtein(left: String, right: String): Int {
            val dp = Array(left.length + 1) { IntArray(right.length + 1) }
            for (i in 0..left.length) dp[i][0] = i
            for (j in 0..right.length) dp[0][j] = j
            for (i in 1..left.length) {
                for (j in 1..right.length) {
                    val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                    dp[i][j] = minOf(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost,
                    )
                }
            }
            return dp[left.length][right.length]
        }
    }
}

class Selectors internal constructor(
    private val values: List<Selector>,
) : List<Selector> by values {
    val length: Int
        get() = values.size

    fun css(
        selector: String,
        identifier: String = "",
        adaptive: Boolean = false,
        autoSave: Boolean = false,
        percentage: Int = 0,
    ): Selectors = Selectors(values.flatMap { it.css(selector, identifier, adaptive, autoSave, percentage) })

    fun xpath(
        selector: String,
        identifier: String = "",
        adaptive: Boolean = false,
        autoSave: Boolean = false,
        percentage: Int = 0,
        variables: Map<String, Any?> = emptyMap(),
    ): Selectors = Selectors(values.flatMap { it.xpath(selector, identifier, adaptive, autoSave, percentage, variables) })

    fun get(default: TextHandler? = null): TextHandler? = values.firstOrNull()?.get() ?: default

    fun getall(): TextHandlers = TextHandlers(values.map { it.get() })

    fun first(): Selector? = values.firstOrNull()

    fun last(): Selector? = values.lastOrNull()
}
