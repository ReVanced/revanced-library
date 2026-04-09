package app.revanced.shizukulibrary.adb

import android.content.Context
import android.os.Build
import android.sun.misc.BASE64Encoder
import android.sun.security.provider.X509Factory
import android.sun.security.x509.*
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

class AdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {
    private var mPrivateKey: PrivateKey? = null
    private var mCertificate: Certificate? = null

    init {
        setApi(Build.VERSION.SDK_INT)
        try {
            mPrivateKey = readPrivateKeyFromFile(context)
            mCertificate = readCertificateFromFile(context)
        } catch (e: Exception) {
            // Log or handle initial read failure
        }

        // Regenerate if key is missing or certificate is expired
        var needsRegeneration = mPrivateKey == null || mCertificate == null
        if (!needsRegeneration && mCertificate is X509Certificate) {
            try {
                (mCertificate as X509Certificate).checkValidity()
            } catch (e: Exception) {
                needsRegeneration = true
            }
        }

        if (needsRegeneration) {
            try {
                // Generate a new key pair
                val keySize = 2048
                val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
                keyPairGenerator.initialize(keySize, SecureRandom.getInstance("SHA1PRNG"))
                val generateKeyPair = keyPairGenerator.generateKeyPair()
                val publicKey = generateKeyPair.public
                mPrivateKey = generateKeyPair.private

                // Generate a new certificate
                val subject = "CN=Revanced Library"
                val algorithmName = "SHA512withRSA"
                val expiryDate = System.currentTimeMillis() + 10L * 365 * 86400000

                val certificateExtensions = CertificateExtensions()
                certificateExtensions.set(
                    "SubjectKeyIdentifier", SubjectKeyIdentifierExtension(
                        KeyIdentifier(publicKey).identifier
                    )
                )
                val x500Name = X500Name(subject)
                val notBefore = Date()
                val notAfter = Date(expiryDate)
                certificateExtensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
                val certificateValidity = CertificateValidity(notBefore, notAfter)
                val x509CertInfo = X509CertInfo()
                x509CertInfo.set("version", CertificateVersion(2))
                x509CertInfo.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
                x509CertInfo.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
                x509CertInfo.set("subject", CertificateSubjectName(x500Name))
                x509CertInfo.set("key", CertificateX509Key(publicKey))
                x509CertInfo.set("validity", certificateValidity)
                x509CertInfo.set("issuer", CertificateIssuerName(x500Name))
                x509CertInfo.set("extensions", certificateExtensions)

                val x509CertImpl = X509CertImpl(x509CertInfo)
                x509CertImpl.sign(mPrivateKey, algorithmName)
                mCertificate = x509CertImpl

                // Write files
                writePrivateKeyToFile(context, mPrivateKey!!)
                writeCertificateToFile(context, mCertificate!!)
            } catch (e: Exception) {
                throw RuntimeException("Failed to generate ADB credentials", e)
            }
        }
    }

    public override fun getPrivateKey(): PrivateKey {
        return mPrivateKey ?: throw IllegalStateException("Private key not initialized")
    }

    public override fun getCertificate(): Certificate {
        return mCertificate ?: throw IllegalStateException("Certificate not initialized")
    }

    override fun getDeviceName(): String {
        return "MyAwesomeApp"
    }

    companion object {
        private var INSTANCE: AdbConnectionManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): AdbConnectionManager {
            if (INSTANCE == null) {
                INSTANCE = AdbConnectionManager(context)
            }
            return INSTANCE!!
        }

        @Throws(IOException::class, CertificateException::class)
        private fun readCertificateFromFile(context: Context): Certificate? {
            val certFile = File(context.filesDir, "cert.pem")
            if (!certFile.exists()) return null
            return FileInputStream(certFile).use { cert ->
                CertificateFactory.getInstance("X.509").generateCertificate(cert)
            }
        }

        @Throws(CertificateEncodingException::class, IOException::class)
        private fun writeCertificateToFile(context: Context, certificate: Certificate) {
            val certFile = File(context.filesDir, "cert.pem")
            val encoder = BASE64Encoder()
            FileOutputStream(certFile).use { os ->
                os.write(X509Factory.BEGIN_CERT.toByteArray(StandardCharsets.UTF_8))
                os.write('\n'.toInt())
                encoder.encode(certificate.encoded, os)
                os.write('\n'.toInt())
                os.write(X509Factory.END_CERT.toByteArray(StandardCharsets.UTF_8))
            }
        }

        @Throws(IOException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        private fun readPrivateKeyFromFile(context: Context): PrivateKey? {
            val privateKeyFile = File(context.filesDir, "private.key")
            if (!privateKeyFile.exists()) return null
            val privKeyBytes = ByteArray(privateKeyFile.length().toInt())
            DataInputStream(FileInputStream(privateKeyFile)).use { dis ->
                dis.readFully(privKeyBytes)
            }
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKeySpec = PKCS8EncodedKeySpec(privKeyBytes)
            return keyFactory.generatePrivate(privateKeySpec)
        }

        @Throws(IOException::class)
        private fun writePrivateKeyToFile(context: Context, privateKey: PrivateKey) {
            val privateKeyFile = File(context.filesDir, "private.key")
            FileOutputStream(privateKeyFile).use { os ->
                os.write(privateKey.encoded)
            }
            // Restrict file permissions to owner only
            privateKeyFile.setReadable(false, false)
            privateKeyFile.setReadable(true, true)
            privateKeyFile.setWritable(false, false)
            privateKeyFile.setWritable(true, true)
        }
    }
}
