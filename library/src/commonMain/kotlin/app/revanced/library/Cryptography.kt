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
import java.security.MessageDigest

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
): PGPSignature = when (val pgpObject = PGPObjectFactory(
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
) = PGPPublicKeyRingCollection(
    PGPUtil.getDecoderStream(publicKeyRingInputStream),
    fingerprintCalculator
)

// endregion

// region SLSA attestation verification.

private val keylessVerifier: KeylessVerifier =
    KeylessVerifier.builder().sigstorePublicDefaults().build()

private const val PROVENANCE_PREDICATE_TYPE = "https://slsa.dev/provenance/v1"

/**
 * Verifies the provenance of the provided artifact bytes
 * using the provided attestation input stream and matcher.
 *
 * @param artifactBytes The bytes of the artifact to verify.
 * @param attestationInputStream The input stream of the attestation.
 * @param buildMatcher The builder for the matcher to use in verification options.
 * @return True if the verification is successful, false otherwise.
 */
fun verifyProvenance(
    artifactBytes: ByteArray,
    attestationInputStream: InputStream,
    buildMatcher: Builder.() -> Builder,
) = verifyProvenance(artifactBytes, attestationInputStream, verificationOptions(buildMatcher))

/**
 * Verifies the provenance of the provided artifact bytes
 * using the provided attestation input stream and verification options.
 *
 * @param artifactBytes The bytes of the artifact to verify.
 * @param attestationInputStream The input stream of the attestation.
 * @param verificationOptions The verification options to use.
 * @return True if the verification is successful, false otherwise.
 */
fun verifyProvenance(
    artifactBytes: ByteArray,
    attestationInputStream: InputStream,
    verificationOptions: VerificationOptions,
) = runCatching {
    val bundle = Bundle.from(attestationInputStream.reader())

    val predicateType = InTotoPayload.from(bundle.dsseEnvelope.get()).predicateType
    require(predicateType == PROVENANCE_PREDICATE_TYPE)

    val artifactDigest = MessageDigest.getInstance("SHA-256").digest(artifactBytes)

    keylessVerifier.verify(artifactDigest, bundle, verificationOptions)
}.isSuccess

/**
 * Creates verification options with the provided matcher.
 *
 * @param buildMatcher The builder for the certificate matcher to add to the verification options.
 * @return The created verification options.
 */
fun verificationOptions(
    buildMatcher: Builder.() -> Builder
): VerificationOptions = VerificationOptions.builder().addCertificateMatchers(
    CertificateMatcher.fulcio().buildMatcher().build()
).build()

private const val ROOT_OID = "1.3.6.1.4.1.57264"
private const val RUNNER_ENVIRONMENT_OID = "$ROOT_OID.1.11"
private const val ISSUER_OID = "$ROOT_OID.1.1"

/**
 * Match GitHub.
 *
 * @param repository The GitHub repository in the format "owner/repo".
 * @return The updated certificate matcher builder to attest GitHub provenance.
 */
fun Builder.matchGitHub(
    repository: String
): Builder = issuer(
    // See: https://github.com/cli/cli/blob/cf862d65df7f8ff528015e235c8cccd48cea286f/pkg/cmd/attestation/verify/policy.go#L116-L117
    StringMatcher.regex(".*")
).subjectAlternativeName(
    StringMatcher.regex("(?i)^https://github.com/$repository/.*$")
).putOidDerAsn1Strings(
    RUNNER_ENVIRONMENT_OID,
    StringMatcher.string("github-hosted")
).putOidRawStrings(
    ISSUER_OID,
    StringMatcher.string("https://token.actions.githubusercontent.com")
)

// endregion
