package ru.viptec.zapretinfo.main

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.gov.rkn.vigruzki.operatorrequest.OperatorRequestService
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.ws.Holder

class Downloader(private val provider: Provider, private val dumpFormatVersion: String, private val service: OperatorRequestService, private val mailer: Mailer) {
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    private val requestFile = File("${provider.workDir}/request.xml")
    private val signedRequestFile = File("${provider.workDir}/request.bin")
    private val certFile = File("${provider.workDir}/provider.pem")

    private val logger = CustomLogger.getLogger(provider)

    private fun makeRequestFile() {
        val requestTime = dateFormatter.format(Date())
        val request = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        request.apply {
            appendChild(createElement("request")).apply {
                appendChild(createElement("requestTime").apply {
                    appendChild(createTextNode(requestTime))
                })
                appendChild(createElement("operatorName").apply {
                    appendChild(createTextNode(provider.name))
                })
                appendChild(createElement("inn").apply {
                    appendChild(createTextNode(provider.inn))
                })
                appendChild(createElement("ogrn").apply {
                    appendChild(createTextNode(provider.ogrn))
                })
                appendChild(createElement("email").apply {
                    appendChild(createTextNode(provider.email))
                })
                xmlStandalone = true
            }
        }

        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
            setOutputProperty(OutputKeys.ENCODING, "windows-1251")
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.METHOD, "xml")
            transform(DOMSource(request), StreamResult(requestFile))
        }
    }

    private suspend fun signFile(): Boolean {
        if (!certFile.exists()) {
            logger.log(Level.WARNING, "Отсутствует файл подписи ${certFile.canonicalPath}. Следующая попытка через 1 минуту")
            mailer.sendMessage("Неудачная выгрузка от ${provider.name}", "Отсутствует файл подписи")
            return false
        }

        //TODO: Переписать с использованием BouncyCastle
        return withContext(Dispatchers.IO) {
            val p = Runtime.getRuntime()
                .exec("openssl smime -sign -in ${requestFile.absolutePath} -out ${signedRequestFile.absolutePath} -signer ${certFile.absolutePath} -outform DER -nodetach")
            val errors = p.errorStream.readBytes()
            if (errors.isNotEmpty()) {
                logger.log(Level.WARNING, String(errors))
                mailer.sendMessage("Неудачная выгрузка от ${provider.name}", "Ошибки в процессе подписания запроса\n ${String(errors)}")
                return@withContext false
            } else {
                return@withContext true
            }
        }
    }

    private fun sendRequest(): String {
        val isSuccess: Holder<Boolean> = Holder()
        val resultComment: Holder<String?> = Holder()
        val archiveCode: Holder<String?> = Holder()

        service.operatorRequestPort.sendRequest(
            requestFile.readBytes(), signedRequestFile.readBytes(), dumpFormatVersion,
            isSuccess, resultComment, archiveCode
        )
        logger.log(Level.INFO, "sendRequest: ${resultComment.value}")
        return archiveCode.value ?: ""
    }

    private suspend fun getResult(code: String): Boolean {
        val isSuccessful = Holder<Boolean>()
        val comment = Holder<String?>()
        val archive = Holder<ByteArray?>()
        val resultCode = Holder<Int>()
        val version = Holder<String?>()
        val operatorName = Holder<String?>()
        val operatorInn = Holder<String?>()

        do {
            delay(TimeUnit.MINUTES.toMillis(1))

            service.operatorRequestPort.getResult(code, isSuccessful, comment, archive, resultCode,
                version, operatorName, operatorInn)
        } while (resultCode.value == 0)

        return when (resultCode.value) {
            1 -> {
                logger.log(Level.INFO, "Success! Saving archive")
                archive.value?.let {
                    File("${provider.workDir}/archives/out.zip").writeBytes(it)
                }
//                File("${provider.workDir}/archives/${provider.inn}-${dateFormatter.format(Date())}").writeBytes(archive.value!!)
                mailer.sendReport(isSuccessful.value, operatorName.value!!)
                true
            }
            else -> {
                logger.log(Level.WARNING, "Fail. ${comment.value}")
                mailer.sendReport(isSuccessful.value, operatorName.value!!)
                false
            }
        }
    }

    suspend fun download(): Boolean {
        makeRequestFile()
        if (!signFile())
            return false
        return when (val code = sendRequest()) {
            "" -> false
            else -> {
                getResult(code)
            }
        }
    }
}