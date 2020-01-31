package ru.viptec.zapretinfo.main

import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class Logger {
    //TODO: логгирование в разные файлы в зависимости от класса/провайдера
    companion object {
        fun getLogger(opName: String): Logger {
            return Logger.getLogger(opName)
        }
    }
}

class Mailer(private val mto: String, private val mfrom: String, private val msrv: String) {
    private val prop = System.getProperties().apply {
        put("mail.smtp.host", msrv)
    }

    fun sendReport(isSuccess: Boolean, operatorName: String) {
        val session = Session.getInstance(prop, null)
        val msg = MimeMessage(session)
        val logger = Logger.getLogger(operatorName)
        val emailSubject: String
        val emailText: String

        if (isSuccess) {
            emailSubject = "Удачная выгрузка от $operatorName"
            emailText = "Выгрузка запрещенных сайтов от $operatorName успешна"
        } else {
            emailSubject = "Проблемы с выгрузкой от $operatorName"
            emailText = "В процессе выгрузки запрещенных сайтов произошел сбой"
        }

        try {
            msg.apply {
                setFrom(InternetAddress(mfrom))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(mto, false))
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

    fun sendMessage(emailSubject: String, emailText: String) {
        val session = Session.getInstance(prop, null)
        val msg = MimeMessage(session)

        msg.apply {
            setFrom(InternetAddress(mfrom))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(mto, false))
            subject = emailSubject
            setText(emailText)
            sentDate = Date()
        }

        session.getTransport("smtp").apply {
            connect()
            sendMessage(msg, msg.allRecipients)
        }.close()
    }
}

data class Provider(val name: String, val inn: String, val ogrn: String, val email: String, val workDir: String, val mto: String, val mfrom: String, val mserver: String)
