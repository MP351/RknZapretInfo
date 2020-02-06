package ru.viptec.zapretinfo.main

import java.util.*
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class CustomLogger {
    companion object {
        fun getLogger(provider: Provider): Logger {
            return Logger.getLogger(provider.name).apply {
                addHandler(FileHandler("${provider.workDir}/logs/watcher.log"))
            }
        }
    }
}

@Suppress("MemberVisibilityCanBePrivate")
class Mailer(private val provider: Provider) {
    private val logger = Logger.getLogger(provider.name)

    private val prop = System.getProperties().apply {
        put("mail.smtp.host", provider.mserver)
    }

    fun sendReport(isSuccess: Boolean, operatorName: String) {
        val emailSubject: String
        val emailText: String

        if (isSuccess) {
            emailSubject = "Удачная выгрузка от $operatorName"
            emailText = "Выгрузка запрещенных сайтов от $operatorName успешна"
        } else {
            emailSubject = "Проблемы с выгрузкой от $operatorName"
            emailText = "В процессе выгрузки запрещенных сайтов произошел сбой"
        }

        sendMessage(emailSubject, emailText)
    }

    fun sendMessage(emailSubject: String, emailText: String) {
        val session = Session.getInstance(prop, null)
        val msg = MimeMessage(session)

        try {
            msg.apply {
                setFrom(InternetAddress(provider.mfrom))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(provider.mto, false))
                subject = emailSubject
                setText(emailText)
                sentDate = Date()
            }

            session.getTransport("smtp").apply {
                connect()
                sendMessage(msg, msg.allRecipients)
            }.close()
        } catch (ex: MessagingException) {
            logger.log(Level.INFO, "Mailer", ex)
        }
    }
}

data class Provider(val name: String, val inn: String, val ogrn: String, val email: String, val workDir: String, val mto: String, val mfrom: String, val mserver: String)
