package io.github.d4vinci.scrapling.parser

import io.github.d4vinci.scrapling.core.storage.SQLiteStorageSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SelectorAdvancedTest {
    private val complexHtml = """
        <html>
          <body>
            <div class="container" data-test='{"key": "value"}'>
              <p>First paragraph</p>
              <p>Second paragraph</p>
              <div class="nested">
                <span id="special">Special content</span>
                <span>Regular content</span>
              </div>
              <table>
                <tr><td>Cell 1</td><td>Cell 2</td></tr>
                <tr><td>Cell 3</td><td>Cell 4</td></tr>
              </table>
            </div>
          </body>
        </html>
    """.trimIndent()

    @Test
    fun xpathSupportsVariables() {
        val page = Selector(complexHtml)

        val cells = page.xpath("//td[text()=\$cell_text]", variables = mapOf("cell_text" to "Cell 1"))

        assertEquals(1, cells.length)
        assertEquals("Cell 1", cells[0].text.value)
    }

    @Test
    fun adaptiveInitializationUsesDefaultStorageWhenEnabled() {
        val page = Selector("<html><body><p>Test</p></body></html>", url = "https://example.com", adaptive = true)

        assertTrue(page.isAdaptiveEnabled)
        assertNotNull(page.storageSystem)
    }

    @Test
    fun adaptiveInitializationUsesProvidedStorage() {
        val storage = SQLiteStorageSystem(storageFile = ":memory:", url = "https://example.com")
        val page = Selector(
            content = "<html><body><p>Test</p></body></html>",
            url = "https://example.com",
            adaptive = true,
            storageSystem = storage,
        )

        assertSame(storage, page.storageSystem)
    }

    @Test
    fun adaptiveRelocatesElementAfterStructureChange() {
        val originalHtml = """
            <div class="container">
              <section class="products">
                <article class="product" id="p1">
                  <h3>Product 1</h3>
                  <p class="description">Description 1</p>
                </article>
                <article class="product" id="p2">
                  <h3>Product 2</h3>
                  <p class="description">Description 2</p>
                </article>
              </section>
            </div>
        """.trimIndent()
        val changedHtml = """
            <div class="new-container">
              <div class="product-wrapper">
                <section class="products">
                  <article class="product new-class" data-id="p1">
                    <div class="product-info">
                      <h3>Product 1</h3>
                      <p class="new-description">Description 1</p>
                    </div>
                  </article>
                  <article class="product new-class" data-id="p2">
                    <div class="product-info">
                      <h3>Product 2</h3>
                      <p class="new-description">Description 2</p>
                    </div>
                  </article>
                </section>
              </div>
            </div>
        """.trimIndent()

        val oldPage = Selector(originalHtml, url = "https://example.com", adaptive = true)
        val newPage = Selector(changedHtml, url = "https://example.com", adaptive = true)

        oldPage.css("#p1, #p2", autoSave = true)
        val relocated = newPage.css("#p1", adaptive = true)

        assertEquals(1, relocated.length)
        assertEquals("p1", relocated[0].attrib["data-id"]?.value)
        assertTrue(relocated[0].hasClass("new-class"))
        assertEquals("Description 1", relocated[0].css(".new-description::text").get()?.value)
    }
}
