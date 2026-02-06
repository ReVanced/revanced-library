@file:Suppress("unused")

package app.revanced.library

import dev.sigstore.KeylessVerifier
import dev.sigstore.VerificationOptions
import dev.sigstore.VerificationOptions.CertificateMatcher
import dev.sigstore.bundle.Bundle
import dev.sigstore.dsse.InTotoPayload
import dev.sigstore.fulcio.client.ImmutableFulcioCertificateMatcher.Builder
import dev.sigstore.strings.StringMatcher
import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import java.io.InputStream

// region PGP signature verification.

private val verifierBuilderProvider = BcPGPContentVerifierBuilderProvider()

private val fingerprintCalculator = BcKeyFingerprintCalculator()

/**
 * Verifies the PGP signature of the provided bytes using the provided signature and public key.
 *
 * @param bytes The bytes to verify.
 * @param signature The PGP signature.
 * @param publicKey The PGP public key.
 * @return True if the signature is valid, false otherwise.
 */
fun verifySignature(
    bytes: ByteArray, signature: PGPSignature, publicKey: PGPPublicKey
) = signature.apply {
    init(verifierBuilderProvider, publicKey)
    update(bytes)
}.verify()

/**
 * Gets the PGP signature from the provided signature input stream.
 *
 * @param signatureInputStream The input stream of the PGP signature.
 * @return The PGP signature.
 * @throws IllegalArgumentException if the signature format is invalid.
 */
fun getSignature(
    signatureInputStream: InputStream
) = when (val pgpObject = PGPObjectFactory(
    PGPUtil.getDecoderStream(signatureInputStream), fingerprintCalculator
).nextObject()) {
    is PGPSignatureList -> pgpObject
    is PGPCompressedData -> {
        val compressedDataFactory = PGPObjectFactory(
            pgpObject.dataStream, fingerprintCalculator
        )
        compressedDataFactory.nextObject() as PGPSignatureList
    }

    else -> throw IllegalArgumentException("Invalid PGP signature format.")
}.first()


/**
 * Gets the PGP public key ring collection from the provided public key ring input stream.
 *
 * @param publicKeyRingInputStream The input stream of the public key ring.
 * @return The PGP public key ring collection.
 */
fun getPublicKeyRingCollection(
    publicKeyRingInputStream: InputStream
) = PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(publicKeyRingInputStream), fingerprintCalculator)

/**
 * Gets the PGP public key ring with the specified key ID from the provided public key ring collection.
 *
 * @param publicKeyRingCollection The PGP public key ring collection.
 * @param keyId The key ID of the public key ring to retrieve.
 * @return The PGP public key ring with the specified key ID.
 * @throws IllegalArgumentException if the public key ring with the specified key ID is not found.
 */
fun getPublicKeyRing(
    publicKeyRingCollection: PGPPublicKeyRingCollection, keyId: Long
) = publicKeyRingCollection.getPublicKeyRing(keyId)
    ?: throw IllegalArgumentException("Can't find public key ring with ID $keyId.")

/**
 * Gets the PGP public key from the provided public key ring.
 */
fun getPublicKey(publicKeyRing: PGPPublicKeyRing): PGPPublicKey = publicKeyRing.publicKey

// endregion

// region SLSA attestation verification.

private val keylessVerifier: KeylessVerifier = KeylessVerifier.builder().sigstorePublicDefaults().build()

private const val RUNNER_ENVIRONMENT_OID = "1.3.6.1.4.1.57264.1.11"

private const val PROVENANCE_PREDICATE_TYPE = "https://slsa.dev/provenance/v1"

/**
 * Verifies the SLSA attestation of the provided digest using the provided attestation input stream and matcher.
 *
 * @param digest The digest to verify.
 * @param attestationInputStream The input stream of the attestation.
 * @param matcher The matcher to add to the verification options.
 * @return True if the verification is successful, false otherwise.
 */
fun verifySLSA(
    digest: ByteArray,
    attestationInputStream: InputStream,
    matcher: Builder.() -> Builder,
) = verifySLSA(digest, attestationInputStream, verificationOptions(matcher))

/**
 * Verifies the SLSA attestation of the provided digest using the provided attestation input stream
 * and verification options.
 *
 * @param digest The digest to verify.
 * @param attestationInputStream The input stream of the attestation.
 * @param verificationOptions The verification options to use.
 * @return True if the verification is successful, false otherwise.
 */
fun verifySLSA(
    digest: ByteArray,
    attestationInputStream: InputStream,
    verificationOptions: VerificationOptions,
) = runCatching {
    val bundle = Bundle.from(attestationInputStream.reader())

    val predicateType = InTotoPayload.from(bundle.dsseEnvelope.get()).predicateType
    require(predicateType == PROVENANCE_PREDICATE_TYPE)

    keylessVerifier.verify(digest, bundle, verificationOptions)
}.isSuccess

/**
 * Creates verification options with the provided matcher.
 *
 * @param matcher The matcher to add to the verification options.
 * @return The created verification options.
 */
fun verificationOptions(
    matcher: Builder.() -> Builder
): VerificationOptions = VerificationOptions.builder().addCertificateMatchers(
    CertificateMatcher.fulcio().matcher().build()
).build()

/**
 * Adds GitHub-specific matching to the builder for the specified repository.
 *
 * @param repository The GitHub repository in the format "owner/repo".
 * @return The updated builder with GitHub-specific matching.
 */
fun Builder.matchGitHub(
    repository: String
): Builder = issuer(
    StringMatcher.string("https://token.actions.githubusercontent.com")
).subjectAlternativeName(
    StringMatcher.regex("(?i)^https://github.com/$repository")
).putOidDerAsn1Strings(RUNNER_ENVIRONMENT_OID, StringMatcher.string("github-hosted"))

// endregion

