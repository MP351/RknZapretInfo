@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package ru.viptec.zapretinfo.main

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ru.gov.rkn.vigruzki.operatorrequest.OperatorRequestService
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.xml.ws.Holder
import kotlin.properties.Delegates

class Watcher(private val provider: Provider) {
    private val service = OperatorRequestService()
    private val lastDumpDateFile = File("${provider.workDir}/lastDumpDate")
    private val logger = CustomLogger.getLogger(provider)
    private val refreshPeriod = TimeUnit.MINUTES.toMillis(1)
    private val mailer = Mailer(provider)

    private val _lastActualDumpDate: Holder<Long?> = Holder()
    val lastActualDumpDate: Long
        get() = _lastActualDumpDate.value ?: 0

    private val _lastDumpDateUrgently: Holder<Long?> = Holder()
    val lastActualUrgentDumpDate: Long
        get() = _lastActualDumpDate.value ?: 0

    private val _webServiceVersion: Holder<String?> = Holder()
    val serviceVersion: String?
        get() = _webServiceVersion.value

    private val _dumpFormatVersion: Holder<String?> = Holder()
    val dumpFormatVersion: String?
        get() = _dumpFormatVersion.value

    private val _docVersion: Holder<String?> = Holder()
    val docVersion: String?
        get() = _docVersion.value

    var isRunning: Boolean by Delegates.observable(false) {
        _, _, newValue ->
        if (newValue)
            runBlocking {
                watch()
            }
    }

    private suspend fun watch() {
            while (isRunning) {
                refreshServiceData()
                if (getLastDumpDate() >= lastActualDumpDate && getLastDumpDate() >= lastActualUrgentDumpDate) {
                    delay(refreshPeriod)
                    continue
                }
                try {
                    if (Downloader(provider, dumpFormatVersion ?: "3.0", service, mailer).download())
                        lastDumpDateFile.writeText(lastActualDumpDate.coerceAtLeast(lastActualUrgentDumpDate).toString())
                } catch (ex: Throwable) {
                    logger.log(Level.WARNING, ex.message, ex)
                    mailer.sendMessage("Неудачная выгрузка от ${provider.name}", "Сбой в работе сервиса\n${ex.message.toString()}")
                }
                delay(refreshPeriod)
        }
    }

    private fun refreshServiceData() {
        try {
            service.operatorRequestPort.getLastDumpDateEx(
                _lastActualDumpDate,
                _lastDumpDateUrgently,
                _webServiceVersion,
                _dumpFormatVersion,
                _docVersion
            )
            logger.log(
                Level.INFO,
                "getLastDumpDate: ldd: ${_lastActualDumpDate.value}, lddu: ${_lastActualDumpDate.value}. " +
                        "current: ${getLastDumpDate()}"
            )
        } catch (ex: Throwable) {
            logger.log(Level.WARNING, ex.message, ex)
            mailer.sendMessage("Неудачная выгрузка от ${provider.name}", "Сбой в работе сервиса\n${ex.message.toString()}")
        }
    }

    private fun getLastDumpDate(): Long {
        if (!lastDumpDateFile.exists()) {
            lastDumpDateFile.writeText(0.toString())
        }

        return lastDumpDateFile.readLines()[0].toLong()
    }
}