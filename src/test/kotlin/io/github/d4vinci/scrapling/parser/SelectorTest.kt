package io.github.d4vinci.scrapling.parser

import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SelectorTest {
    private val html = """
        <html>
          <body>
            <section id="products" data-meta='{"kind":"catalog"}'>
              <h2>Products</h2>
              <div class="product-list">
                <article class="product featured" data-id="1">
                  <h3>Product 1</h3>
                  <span class="price">$10.99</span>
                </article>
                <article class="product" data-id="2">
                  <h3>Product 2</h3>
                  <span class="price">$20.99</span>
                </article>
              </div>
            </section>
          </body>
        </html>
    """.trimIndent()

    @Test
    fun directTextAndRecursiveTextStayDifferent() {
        val selector = Selector(html)
        val section = selector.find("section#products")

        assertNotNull(section)
        assertEquals("", section.text.value)
        assertEquals("Products Product 1 $10.99 Product 2 $20.99", section.getAllText().value)
    }

    @Test
    fun cssQueriesReturnSelectorCollections() {
        val selector = Selector(html)
        val products = selector.css("article.product")

        assertEquals(2, products.length)
        assertEquals("article", products.first()?.tag)
        assertEquals("2", products.last()?.attrib?.get("data-id")?.value)
    }

    @Test
    fun attributesAndNavigationExposeRichTypes() {
        val selector = Selector(html)
        val section = selector.find("section#products")
        val firstProduct = selector.find("article.product")

        assertNotNull(section)
        assertNotNull(firstProduct)
        assertEquals("catalog", section.attrib.get("data-meta")?.json()?.jsonObject?.get("kind")?.toString()?.trim('"'))
        assertEquals(2, section.children().length)
        assertEquals(1, firstProduct.siblings().length)
        assertTrue(firstProduct.htmlContent.contains("Product 1"))
    }

    @Test
    fun htmlContentGetAndPrettifyUseSerializedNodeSemantics() {
        val selector = Selector(html)
        val firstProduct = selector.find("article.product")

        assertNotNull(firstProduct)
        assertTrue(firstProduct.htmlContent.startsWith("<article"))
        assertEquals(firstProduct.htmlContent, firstProduct.get().value)
        assertEquals(listOf(firstProduct.htmlContent), firstProduct.getall().extract())
        assertTrue(firstProduct.prettify().value.contains("\n"))
    }

    @Test
    fun pseudoSelectorsSupportTextAndAttributes() {
        val selector = Selector(html)

        assertEquals("Product 1", selector.css("article.product h3::text").get()?.value)
        assertEquals(listOf("$10.99", "$20.99"), selector.css("article.product .price::text").getall().extract())
        assertEquals(listOf("1", "2"), selector.css("article.product::attr(data-id)").getall().extract())
    }

    @Test
    fun parentNextPreviousAndCollectionSelectionStayComposable() {
        val selector = Selector(html)
        val products = selector.css("article.product")
        val firstProduct = products.first()
        val secondProduct = products.last()

        assertNotNull(firstProduct)
        assertNotNull(secondProduct)
        assertEquals("div", firstProduct.parent()?.tag)
        assertEquals("2", firstProduct.next()?.attrib?.get("data-id")?.value)
        assertEquals("1", secondProduct.previous()?.attrib?.get("data-id")?.value)
        assertEquals(listOf("Product 1", "Product 2"), products.css("h3::text").getall().extract())
    }
}
