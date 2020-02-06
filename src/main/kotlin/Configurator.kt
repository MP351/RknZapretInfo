package ru.viptec.zapretinfo.main

import java.io.File
import java.io.FileFilter
import java.lang.Exception
import java.nio.charset.Charset
import java.util.*
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class Configurator(private val configsDirPath: String) {
    private val logger = Logger.getLogger("Configurator").apply {
        addHandler(FileHandler("logs/rzi.log"))
    }
    private val workDirsPrefix = "operators"

    fun getProvidersList(): List<Provider> {
        val providers = ArrayList<Provider>()
        getConfigList().forEach {
            val properties = Properties().apply {
                load(it.inputStream())
            }
            try {
                val provider =
                    with(properties) {
                        Provider(
                            String(getProperty("name").byteInputStream(Charset.forName("ISO8859-1")).readBytes()),
                            getProperty("inn"),
                            getProperty("ogrn"),
                            getProperty("email"),
                            "$workDirsPrefix/${getProperty("workDir")}",
                            getProperty("mto"),
                            getProperty("mfrom"),
                            getProperty("mserver")
                        )
                    }
                providers.add(provider)
            } catch (ex: Exception) {
                logger.log(Level.WARNING, "Config parse error", ex)
            }
        }
        return providers
    }

    private fun getConfigList(): Array<File> {
        val configDirFile = File(configsDirPath)
        val pattern = Pattern.compile("^\\d+-.{3,}\\.properties$")

        return configDirFile.listFiles(FileFilter {
            pattern.matcher(it.name).matches()
        }) ?: arrayOf()
    }
}