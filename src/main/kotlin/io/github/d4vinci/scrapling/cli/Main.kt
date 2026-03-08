package io.github.d4vinci.scrapling.cli

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(ScraplingCli().run(args))
}
