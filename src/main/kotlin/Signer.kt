package ru.viptec.zapretinfo.main

import java.io.ByteArrayInputStream
import java.io.File
import java.security.Key
import java.security.KeyStore
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.X509Certificate

class Signer {
//    private val bcProvider = BouncyCastleProvider()
    private val keyStoreFile = File("crt/viptec_rkn.pkcs12")
    private val keyStorePassword = "123"
    private lateinit var keyStore: KeyStore

    val keys = HashMap<String, Key>()
    val certs = HashMap<String, X509Certificate>()

    init {
//        Security.removeProvider(bcProvider.name)
//        Security.addProvider(bcProvider)
    }

    fun readPfx() {
        keyStore = KeyStore.getInstance("PKCS12", "BC")
        val baos = ByteArrayInputStream(keyStoreFile.readBytes())
        keyStore.load(baos, keyStorePassword.toCharArray())

        for (alias in keyStore.aliases()) {
            if (keyStore.isKeyEntry(alias)) {
                val key = keyStore.getKey(alias, charArrayOf())
                keys[alias.toLowerCase()] = key

                val cert = keyStore.getCertificate(alias)
                if (cert is X509Certificate)
                    certs[alias.toLowerCase()] = cert
            }
        }
    }
}