package io.github.d4vinci.scrapling.core

import io.github.d4vinci.scrapling.parser.Selector
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CoreTypesTest {
    @Test
    fun textHandlerSupportsWhitespaceJsonAndRegex() {
        val handler = TextHandler("  {\n  \"kind\": \"catalog\", \"page\": 2\n}  ")

        assertEquals("{ \"kind\": \"catalog\", \"page\": 2 }", handler.clean().value)
        assertEquals("catalog", handler.json()?.jsonObject?.get("kind")?.toString()?.trim('"'))
        assertEquals(listOf("2"), handler.re("\"page\":\\s*(\\d+)", 1))
        assertEquals("2", handler.reFirst("\"page\":\\s*(\\d+)", 1))
    }

    @Test
    fun textHandlersAggregateExtractAndRegexResults() {
        val handlers = TextHandlers(listOf(TextHandler("id=1"), TextHandler("id=2")))

        assertEquals(listOf("id=1", "id=2"), handlers.extract())
        assertEquals(listOf("1", "2"), handlers.re("id=(\\d+)", 1))
        assertEquals("1", handlers.reFirst("id=(\\d+)", 1))
    }

    @Test
    fun attributesHandlerSupportsLookupSearchAndJsonString() {
        val selector = Selector("<section id='products' data-meta='{\"kind\":\"catalog\"}' class='catalog root'></section>")
        val section = selector.find("section")

        assertNotNull(section)
        assertEquals("products", section.attrib["id"]?.value)
        assertEquals("catalog", section.attrib.jsonString("data-meta")?.jsonObject?.get("kind")?.toString()?.trim('"'))
        assertEquals(setOf("class", "data-meta"), section.attrib.searchValues("catalog").keys)
    }
}
