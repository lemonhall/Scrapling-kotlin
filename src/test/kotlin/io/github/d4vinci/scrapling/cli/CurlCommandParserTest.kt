package io.github.d4vinci.scrapling.cli

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CurlCommandParserTest {
    private val parser = CurlCommandParser()

    @Test
    fun parsesBasicCurlCommand() {
        val request = parser.parse("curl https://example.com")

        assertEquals("get", request.method)
        assertEquals("https://example.com", request.url)
        assertTrue(request.params.isEmpty())
        assertEquals(null, request.data)
        assertEquals(null, request.jsonData)
        assertTrue(request.headers.isEmpty())
        assertTrue(request.cookies.isEmpty())
        assertEquals(true, request.followRedirects)
    }

    @Test
    fun parsesHeadersCookiesJsonAndProxy() {
        val request = parser.parse(
            """curl https://example.com/api -X POST -H 'User-Agent: TestAgent/1.0' -H 'Cookie: session=abc123; theme=dark' -b 'extra=cookie' --data-raw '{"key":"value"}' -x http://proxy:8080 -U user:pass""",
        )

        assertEquals("post", request.method)
        assertEquals("https://example.com/api", request.url)
        assertEquals("TestAgent/1.0", request.headers["User-Agent"])
        assertEquals("abc123", request.cookies["session"])
        assertEquals("dark", request.cookies["theme"])
        assertEquals("cookie", request.cookies["extra"])
        assertEquals("value", request.jsonData?.jsonObject?.get("key")?.jsonPrimitive?.content)
        assertEquals(null, request.data)
        assertEquals("http://user:pass@proxy:8080", request.proxy?.get("http"))
        assertEquals("http://user:pass@proxy:8080", request.proxy?.get("https"))
    }

    @Test
    fun rejectsUnsupportedCurlArguments() {
        val error = assertFailsWith<IllegalArgumentException> {
            parser.parse("curl https://example.com --trace-time")
        }

        assertTrue(error.message.orEmpty().contains("Unknown/Unsupported curl arguments"))
    }

    @Test
    fun convertToRequestOptionsMapsFormEncodedBody() {
        val request = parser.parse("""curl https://example.com/form -X POST -d 'foo=bar&baz=qux'""")
        val options = parser.toRequestOptions(request)

        assertEquals("foo=bar&baz=qux", request.data, "request=$request")
        assertEquals("bar", options.data["foo"])
        assertEquals("qux", options.data["baz"])
    }
}
