package io.github.d4vinci.scrapling.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScraplingCliTest {
    @Test
    fun mainHelpListsCommands() {
        val output = mutableListOf<String>()
        val cli = ScraplingCli(out = output::add, err = output::add)

        val exitCode = cli.run(emptyList())

        assertEquals(0, exitCode)
        assertTrue(output.joinToString("\n").contains("install"))
        assertTrue(output.joinToString("\n").contains("extract"))
        assertTrue(output.joinToString("\n").contains("mcp"))
    }

    @Test
    fun installUsesInstallerAndHonorsForceFlag() {
        val output = mutableListOf<String>()
        val calls = mutableListOf<Boolean>()
        val cli = ScraplingCli(
            out = output::add,
            err = output::add,
            installer = DependencyInstaller { force ->
                calls += force
                "installed"
            },
        )

        val exitCode = cli.run(listOf("install", "--force"))

        assertEquals(0, exitCode)
        assertEquals(listOf(true), calls)
        assertTrue(output.single().contains("installed"))
    }

    @Test
    fun shellRunsCodeAndPrintsNamespace() {
        val output = mutableListOf<String>()
        val cli = ScraplingCli(out = output::add, err = output::add)

        val exitCode = cli.run(listOf("shell", "-c", "namespace", "-L", "info"))

        assertEquals(0, exitCode)
        assertTrue(output.joinToString("\n").contains("Fetcher"))
        assertTrue(output.joinToString("\n").contains("uncurl"))
    }

    @Test
    fun shellUncurlCommandPrintsStructuredRequestJson() {
        val output = mutableListOf<String>()
        val cli = ScraplingCli(out = output::add, err = output::add)

        val exitCode = cli.run(listOf("shell", "-c", "uncurl(\"curl https://example.com\")"))

        assertEquals(0, exitCode)
        val payload = Json.parseToJsonElement(output.joinToString("\n")).jsonObject
        assertEquals("get", payload["method"]?.jsonPrimitive?.content)
        assertEquals("https://example.com", payload["url"]?.jsonPrimitive?.content)
    }
}
