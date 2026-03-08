package io.github.d4vinci.scrapling.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectorAdvancedUtilitiesTest {
    @Test
    fun selectorCanSaveRetrieveAndRespectThresholdDuringRelocation() {
        val originalHtml = """
            <div class="catalog">
              <article class="product" id="p1"><h3>Product 1</h3></article>
            </div>
        """.trimIndent()
        val changedHtml = """
            <div class="catalog-v2">
              <section>
                <article class="product promoted" data-id="p1"><h3>Product 1</h3></article>
              </section>
            </div>
        """.trimIndent()

        val oldPage = Selector(originalHtml, url = "https://example.com", adaptive = true)
        val newPage = Selector(changedHtml, url = "https://example.com", adaptive = true)
        val original = oldPage.css("#p1").first()

        assertNotNull(original)
        oldPage.save(original, "product-1")

        val snapshot = newPage.retrieve("product-1")
        assertNotNull(snapshot)

        val relocated = newPage.relocate(snapshot, percentage = 0)
        assertEquals(1, relocated.length)
        assertEquals("p1", relocated[0].attrib["data-id"]?.value)
        assertEquals(0, newPage.relocate(snapshot, percentage = 101).length)
    }

    @Test
    fun selectorSupportsRegexAndUrlJoinHelpers() {
        val page = Selector(
            content = "<div><a href='/items/1'>Item 1</a><a href='/items/2'>Item 2</a></div>",
            url = "https://example.com/path/page",
        )

        assertEquals(listOf("/items/1", "/items/2"), page.css("a::attr(href)").re("/items/\\d+"))
        assertEquals("1", page.css("a::attr(href)").reFirst("/items/(\\d+)", 1))
        assertEquals("https://example.com/other", page.urlJoin("../other"))
        assertEquals("https://example.com/absolute", page.urlJoin("/absolute"))
        assertEquals("https://example.com/path/relative", page.urlJoin("relative"))
    }

    @Test
    fun selectorsFilterAndGetDefaultWork() {
        val page = Selector(
            """
            <div>
              <p class='highlight'>Important</p>
              <p>Regular</p>
              <p class='highlight'>Also important</p>
            </div>
            """.trimIndent(),
        )

        val highlighted = page.css("p").filter { it.hasClass("highlight") }

        assertEquals(2, highlighted.length)
        assertEquals("<p class=\"highlight\">Important</p>", highlighted.get()?.value)
        assertEquals("fallback", Selectors(emptyList()).get(default = io.github.d4vinci.scrapling.core.TextHandler("fallback"))?.value)
    }

    @Test
    fun selectorGenerationUtilitiesProduceStablePaths() {
        val page = Selector(
            """
            <body>
              <main>
                <section id='products'>
                  <article class='product'><h3>One</h3></article>
                  <article class='product'><h3>Two</h3></article>
                </section>
              </main>
            </body>
            """.trimIndent(),
        )
        val secondArticle = page.css("article.product").last()

        assertNotNull(secondArticle)
        assertEquals("#products > article:nth-of-type(2)", secondArticle.generateCssSelector)
        assertEquals("body > main > #products > article:nth-of-type(2)", secondArticle.generateFullCssSelector)
        assertEquals("//*[@id='products']/article[2]", secondArticle.generateXpathSelector)
        assertEquals("//body/main/[@id='products']/article[2]", secondArticle.generateFullXpathSelector)
    }
}
