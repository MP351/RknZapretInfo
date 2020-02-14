package ru.viptec.zapretinfo.main

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

fun main(args: Array<String>) {
    LogManager.getLogManager().readConfiguration(FileInputStream("logging.properties"))
    val configs = Configurator("configs/")
    val providers = configs.getProvidersList()
    val logger = Logger.getLogger("INIT").apply {
        addHandler(FileHandler("logs/rzi.log"))
    }

    if (args.isNotEmpty())
        if (args[0] == "--init") {
            createFolders(providers)
            return
        }

    if (providers.isEmpty()) {
        logger.log(Level.WARNING, "Cant find provider configurations")
        return
    }
    logger.log(Level.INFO, "INIT")


    runBlocking {
        providers.forEach {
            launch {
                withContext(Dispatchers.IO) {
                    CustomLogger.getLogger(provider = it).apply {
                        addHandler(FileHandler("${it.workDir}/logs/watcher.log", 5_120_000, 10, true))
                    }
                }
                Watcher(it).isRunning = true
            }
        }
    }
}

fun createFolders(providers: List<Provider>) {
    if (providers.isEmpty())
        return

    providers.forEach {
        File("${it.workDir}/archives").mkdirs()
        File("${it.workDir}/logs").mkdirs()
    }
}
