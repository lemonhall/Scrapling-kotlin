package io.github.d4vinci.scrapling.cli

import io.github.d4vinci.scrapling.ai.McpServerRuntime
import io.github.d4vinci.scrapling.ai.ScraplingMcpServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScraplingCliMcpCommandTest {
    @Test
    fun mcpCommandPrintsTransportConfiguration() {
        val output = mutableListOf<String>()
        val cli = ScraplingCli(
            out = output::add,
            err = output::add,
            mcpFactory = {
                ScraplingMcpServer(
                    runtime = McpServerRuntime { _, launch, _, sink ->
                        sink("runtime ${launch.transport} ${launch.host}:${launch.port}")
                    },
                )
            },
        )

        val exitCode = cli.run(listOf("mcp", "--http", "--host", "127.0.0.1", "--port", "9000"))

        assertEquals(0, exitCode)
        val joined = output.joinToString("\n")
        assertTrue(joined.contains("streamable-http"))
        assertTrue(joined.contains("127.0.0.1:9000"))
    }
}

