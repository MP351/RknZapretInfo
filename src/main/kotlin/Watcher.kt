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
    private val logger = Logger.getLogger("Watcher of ${provider.name}")
    private val refreshPeriod = TimeUnit.MINUTES.toMillis(1)

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
        property, oldValue, newValue ->
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
                if (Downloader(provider, dumpFormatVersion ?: "3.0").download())
                    lastDumpDateFile.writeText(lastActualDumpDate.coerceAtLeast(lastActualUrgentDumpDate).toString())
        }
    }

    private fun refreshServiceData() {
        service.operatorRequestPort.getLastDumpDateEx(_lastActualDumpDate, _lastDumpDateUrgently, _webServiceVersion, _dumpFormatVersion, _docVersion)
        logger.log(
            Level.INFO, "getLastDumpDate result: Ldd: ${_lastActualDumpDate.value}, Lddu: ${_lastActualDumpDate.value}. " +
                    "Current: ${getLastDumpDate()}")
    }

    private fun getLastDumpDate(): Long {
        if (!lastDumpDateFile.exists()) {
            lastDumpDateFile.writeText(0.toString())
        }

        return lastDumpDateFile.readLines()[0].toLong()
    }
}