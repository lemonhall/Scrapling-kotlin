package io.github.d4vinci.scrapling.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class JdkMcpServerRuntime(
    private val input: InputStream = System.`in`,
    private val output: OutputStream = System.out,
    private val json: Json = mcpJson,
) : McpServerRuntime {
    override fun serve(serverName: String, launch: McpLaunchResult, handler: McpProtocolHandler, sink: (String) -> Unit) {
        when (launch.transport) {
            "streamable-http" -> serveHttp(serverName, launch, handler, sink)
            "stdio" -> serveStdio(launch, handler, sink)
            else -> error("Unsupported MCP transport: ${launch.transport}")
        }
    }

    private fun serveHttp(serverName: String, launch: McpLaunchResult, handler: McpProtocolHandler, sink: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress(launch.host, launch.port), 0)
        server.createContext("/health") { exchange ->
            writeJson(exchange, 200, McpHealthResponse(name = serverName, status = "ready", transport = launch.transport))
        }
        server.createContext("/mcp") { exchange ->
            when (exchange.requestMethod.uppercase()) {
                "GET" -> writeJson(exchange, 405, McpErrorResponse(error = "Use POST /mcp for JSON-RPC requests"))
                "POST" -> handleHttpRpc(exchange, handler)
                else -> writeJson(exchange, 405, McpErrorResponse(error = "Unsupported HTTP method: ${exchange.requestMethod}"))
            }
        }
        val stopSignal = CountDownLatch(1)
        server.start()
        sink("Scrapling MCP server ready via ${launch.transport} on ${launch.host}:${launch.port}")
        try {
            stopSignal.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            server.stop(0)
        }
    }

    private fun serveStdio(launch: McpLaunchResult, handler: McpProtocolHandler, sink: (String) -> Unit) {
        val reader = input.bufferedReader(StandardCharsets.UTF_8)
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
        sink("Scrapling MCP server ready via ${launch.transport} on ${launch.host}:${launch.port}")
        while (true) {
            val line = reader.readLine() ?: break
            handler.handle(line)?.let { response ->
                writer.write(response)
                writer.write("\n")
                writer.flush()
            }
        }
    }

    private fun handleHttpRpc(exchange: HttpExchange, handler: McpProtocolHandler) {
        val request = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val response = handler.handle(request)
        if (response == null) {
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(202, -1)
            exchange.close()
            return
        }
        val body = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { stream ->
            stream.write(body)
        }
    }

    private inline fun <reified T> writeJson(exchange: HttpExchange, status: Int, payload: T) {
        val body = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { stream ->
            stream.write(body)
        }
    }
}

@Serializable
private data class McpHealthResponse(
    val name: String,
    val status: String,
    val transport: String,
)

@Serializable
private data class McpErrorResponse(
    val error: String,
)
