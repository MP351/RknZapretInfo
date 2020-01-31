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

class Downloader(private val isp: Provider, private val dumpFormatVersion: String) {
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    private val service = OperatorRequestService()
    private val requestFile = File("${isp.workDir}/request.xml")
    private val signedRequestFile = File("${isp.workDir}/request.bin")
    private val certFile = File("${isp.workDir}/provider.pem")

    private val logger = Logger.getLogger(isp.name)
    private val mailer = Mailer(isp.mto, isp.mfrom, isp.mserver)

    private fun makeRequestFile() {
        val requestTime = dateFormatter.format(Date())
        val request = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        request.apply {
            appendChild(createElement("request")).apply {
                appendChild(createElement("requestTime").apply {
                    appendChild(createTextNode(requestTime))
                })
                appendChild(createElement("operatorName").apply {
                    appendChild(createTextNode(isp.name))
                })
                appendChild(createElement("inn").apply {
                    appendChild(createTextNode(isp.inn))
                })
                appendChild(createElement("ogrn").apply {
                    appendChild(createTextNode(isp.ogrn))
                })
                appendChild(createElement("email").apply {
                    appendChild(createTextNode(isp.email))
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

    private suspend fun signFile() {
        //TODO: Переписать с использованием BouncyCastle
        withContext(Dispatchers.IO) {
            Runtime.getRuntime()
                .exec("openssl smime -sign -in ${requestFile.absolutePath} -out ${signedRequestFile.absolutePath} -signer ${certFile.absolutePath} -outform DER -nodetach")
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
        logger.log(Level.INFO, "sendRequest result: ${isSuccess.value}, ${resultComment.value}")
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
                File("${isp.workDir}/archives/${isp.inn}-${dateFormatter.format(Date())}").writeBytes(archive.value!!)
                mailer.sendReport(isSuccessful.value, operatorName.value!!)
                true
            }
            else -> {
                logger.log(Level.INFO, "Fail. ${comment.value}")
                mailer.sendReport(isSuccessful.value, operatorName.value!!)
                false
            }
        }
    }

    suspend fun download(): Boolean {
        makeRequestFile()
        signFile()
        return when (val code = sendRequest()) {
            "" -> false
            else -> {
                getResult(code)
            }
        }
    }
}