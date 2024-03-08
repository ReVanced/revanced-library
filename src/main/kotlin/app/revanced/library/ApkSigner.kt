package app.revanced.library

import com.android.tools.build.apkzlib.sign.SigningExtension
import com.android.tools.build.apkzlib.sign.SigningOptions
import com.android.tools.build.apkzlib.zip.ZFile
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import java.util.logging.Logger
import kotlin.time.Duration.Companion.days

/**
 * Utility class for reading or writing keystore files and entries as well as signing APK files.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object ApkSigner {
    private val logger = Logger.getLogger(ApkSigner::class.java.name)

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Create a new [PrivateKeyCertificatePair].
     *
     * @param commonName The common name of the certificate.
     * @param validUntil The date until the certificate is valid.
     *
     * @return The created [PrivateKeyCertificatePair].
     */
    fun newPrivateKeyCertificatePair(
        commonName: String = "ReVanced",
        validUntil: Date = Date(System.currentTimeMillis() + (365.days * 8).inWholeMilliseconds * 24),
    ): PrivateKeyCertificatePair {
        logger.fine("Creating certificate for $commonName")

        // Generate a new key pair.
        val keyPair =
            KeyPairGenerator.getInstance("RSA").apply {
                initialize(4096)
            }.generateKeyPair()

        var serialNumber: BigInteger
        do serialNumber = BigInteger.valueOf(SecureRandom().nextLong())
        while (serialNumber < BigInteger.ZERO)

        val name = X500Name("CN=$commonName")

        // Create a new certificate.
        val certificate =
            JcaX509CertificateConverter().getCertificate(
                X509v3CertificateBuilder(
                    name,
                    serialNumber,
                    Date(System.currentTimeMillis()),
                    validUntil,
                    Locale.ENGLISH,
                    name,
                    SubjectPublicKeyInfo.getInstance(keyPair.public.encoded),
                ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)),
            )

        return PrivateKeyCertificatePair(keyPair.private, certificate)
    }

    /**
     * Read a [PrivateKeyCertificatePair] from a keystore entry.
     *
     * @param keyStore The keystore to read the entry from.
     * @param keyStoreEntryAlias The alias of the key store entry to read.
     * @param keyStoreEntryPassword The password for recovering the signing key.
     *
     * @return The read [PrivateKeyCertificatePair].
     *
     * @throws IllegalArgumentException If the keystore does not contain the given alias or the password is invalid.
     */
    fun readKeyCertificatePair(
        keyStore: KeyStore,
        keyStoreEntryAlias: String,
        keyStoreEntryPassword: String,
    ): PrivateKeyCertificatePair {
        logger.fine("Reading key and certificate pair from keystore entry $keyStoreEntryAlias")

        if (!keyStore.containsAlias(keyStoreEntryAlias)) {
            throw IllegalArgumentException("Keystore does not contain alias $keyStoreEntryAlias")
        }

        // Read the private key and certificate from the keystore.

        val privateKey =
            try {
                keyStore.getKey(keyStoreEntryAlias, keyStoreEntryPassword.toCharArray()) as PrivateKey
            } catch (exception: UnrecoverableKeyException) {
                throw IllegalArgumentException("Invalid password for keystore entry $keyStoreEntryAlias")
            }

        val certificate = keyStore.getCertificate(keyStoreEntryAlias) as X509Certificate

        return PrivateKeyCertificatePair(privateKey, certificate)
    }

    /**
     * Create a new keystore with a new keypair.
     *
     * @param entries The entries to add to the keystore.
     *
     * @return The created keystore.
     *
     * @see KeyStoreEntry
     */
    fun newKeyStore(entries: Set<KeyStoreEntry>): KeyStore {
        logger.fine("Creating keystore")

        return newKeyStoreInstance().apply {
            load(null)

            entries.forEach { entry ->
                // Add all entries to the keystore.
                setKeyEntry(
                    entry.alias,
                    entry.privateKeyCertificatePair.privateKey,
                    entry.password.toCharArray(),
                    arrayOf(entry.privateKeyCertificatePair.certificate),
                )
            }
        }
    }

    private fun newKeyStoreInstance() = KeyStore.getInstance("BKS", BouncyCastleProvider.PROVIDER_NAME)

    /**
     * Create a new keystore with a new keypair and saves it to the given [keyStoreOutputStream].
     *
     * @param keyStoreOutputStream The stream to write the keystore to.
     * @param keyStorePassword The password for the keystore.
     * @param entries The entries to add to the keystore.
     */
    fun newKeyStore(
        keyStoreOutputStream: OutputStream,
        keyStorePassword: String,
        entries: Set<KeyStoreEntry>,
    ) = newKeyStore(entries).store(
        keyStoreOutputStream,
        keyStorePassword.toCharArray(),
    )

    /**
     * Read a keystore from the given [keyStoreInputStream].
     *
     * @param keyStoreInputStream The stream to read the keystore from.
     * @param keyStorePassword The password for the keystore.
     *
     * @return The keystore.
     *
     * @throws IllegalArgumentException If the keystore password is invalid.
     */
    fun readKeyStore(
        keyStoreInputStream: InputStream,
        keyStorePassword: String?,
    ): KeyStore {
        logger.fine("Reading keystore")

        return newKeyStoreInstance().apply {
            try {
                load(keyStoreInputStream, keyStorePassword?.toCharArray())
            } catch (exception: IOException) {
                if (exception.cause is UnrecoverableKeyException) {
                    throw IllegalArgumentException("Invalid keystore password")
                } else {
                    throw exception
                }
            }
        }
    }

    /**
     * Create a new [Signer].
     *
     * @param privateKeyCertificatePair The private key and certificate pair to use for signing.
     *
     * @return The new [Signer].
     *
     * @see PrivateKeyCertificatePair
     * @see Signer
     */
    fun newApkSigner(privateKeyCertificatePair: PrivateKeyCertificatePair) =
        Signer(
            SigningExtension(
                SigningOptions.builder()
                    .setMinSdkVersion(21) // TODO: Extracting from the target APK would be ideal.
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setCertificates(privateKeyCertificatePair.certificate)
                    .setKey(privateKeyCertificatePair.privateKey)
                    .build(),
            ),
        )

    /**
     * Create a new [Signer].
     *
     * @param keyStore The keystore to use for signing.
     * @param keyStoreEntryAlias The alias of the key store entry to use for signing.
     * @param keyStoreEntryPassword The password for recovering the signing key.
     *
     * @return The new [Signer].
     *
     * @see KeyStore
     * @see Signer
     */
    fun newApkSigner(
        keyStore: KeyStore,
        keyStoreEntryAlias: String,
        keyStoreEntryPassword: String,
    ) = newApkSigner(readKeyCertificatePair(keyStore, keyStoreEntryAlias, keyStoreEntryPassword))

    /**
     * An entry in a keystore.
     *
     * @param alias The alias of the entry.
     * @param password The password for recovering the signing key.
     * @param privateKeyCertificatePair The private key and certificate pair.
     *
     * @see PrivateKeyCertificatePair
     */
    class KeyStoreEntry(
        val alias: String,
        val password: String,
        val privateKeyCertificatePair: PrivateKeyCertificatePair = newPrivateKeyCertificatePair(),
    )

    /**
     * A private key and certificate pair.
     *
     * @param privateKey The private key.
     * @param certificate The certificate.
     */
    class PrivateKeyCertificatePair(
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
    )

    class Signer internal constructor(private val signingExtension: SigningExtension) {
        /**
         * Sign an APK file.
         *
         * @param apkFile The APK file to sign.
         */
        fun signApk(apkFile: File) = ZFile.openReadWrite(apkFile).use { signApk(it) }

        /**
         * Sign an APK file.
         *
         * @param apkZFile The APK [ZFile] to sign.
         */
        fun signApk(apkZFile: ZFile) {
            logger.info("Signing ${apkZFile.file.name}")

            signingExtension.register(apkZFile)
        }
    }
}
