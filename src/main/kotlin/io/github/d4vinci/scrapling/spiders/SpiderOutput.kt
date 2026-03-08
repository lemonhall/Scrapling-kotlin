package io.github.d4vinci.scrapling.spiders

sealed interface SpiderOutput {
    data class Item(
        val value: Map<String, Any?>,
    ) : SpiderOutput

    data class Follow(
        val request: Request,
    ) : SpiderOutput
}

fun item(value: Map<String, Any?>): SpiderOutput = SpiderOutput.Item(value)

fun follow(request: Request): SpiderOutput = SpiderOutput.Follow(request)
