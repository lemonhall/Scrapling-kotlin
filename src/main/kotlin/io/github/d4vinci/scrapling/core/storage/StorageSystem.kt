package io.github.d4vinci.scrapling.core.storage

import kotlinx.serialization.Serializable
import org.jsoup.nodes.Element

interface StorageSystem {
    val url: String?

    fun save(element: ElementSnapshot, identifier: String)

    fun retrieve(identifier: String): ElementSnapshot?

    fun close() = Unit
}

@Serializable
data class ElementSnapshot(
    val tag: String,
    val attributes: Map<String, String>,
    val text: String?,
    val allText: String?,
    val path: List<String>,
    val parentName: String? = null,
    val parentAttributes: Map<String, String> = emptyMap(),
    val parentText: String? = null,
    val siblings: List<String> = emptyList(),
    val children: List<String> = emptyList(),
)

object StorageTools {
    fun elementToSnapshot(element: Element): ElementSnapshot {
        val parent = element.parent()

        return ElementSnapshot(
            tag = element.tagName(),
            attributes = cleanAttributes(element),
            text = element.ownText().trim().ifBlank { null },
            allText = element.text().trim().ifBlank { null },
            path = element.parents().asReversed().map { it.tagName() } + element.tagName(),
            parentName = parent?.tagName(),
            parentAttributes = parent?.let(::cleanAttributes).orEmpty(),
            parentText = parent?.ownText()?.trim()?.ifBlank { null },
            siblings = element.siblingElements().map { it.tagName() },
            children = element.children().map { it.tagName() },
        )
    }

    private fun cleanAttributes(element: Element): Map<String, String> =
        element.attributes().associate { it.key to it.value.trim() }.filterValues { it.isNotEmpty() }
}

