package ru.viptec.zapretinfo.main

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.util.logging.Level
import java.util.logging.LogManager

fun main(args: Array<String>) {
    LogManager.getLogManager().readConfiguration(FileInputStream("logging.properties"))
    val configs = Configurator("configs/")
    val providers = configs.getProvidersList()
    val logger = Logger.getLogger("INIT")

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


    //FIXME: Watcher бывает отключается при нехватке памяти
    runBlocking {
        providers.forEach {
            launch {
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
    }
}
