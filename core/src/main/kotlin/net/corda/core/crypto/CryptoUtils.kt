@file:JvmName("CryptoUtils")

package net.corda.core.crypto

import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.OpaqueBytes
import java.math.BigInteger
import java.security.*

@CordaSerializable
object NullPublicKey : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "NULL"
    override fun getEncoded() = byteArrayOf(0)
    override fun getFormat() = "NULL"
    override fun compareTo(other: PublicKey): Int = if (other == NullPublicKey) 0 else -1
    override fun toString() = "NULL_KEY"
}

val NULL_PARTY = AnonymousParty(NullPublicKey)

// TODO: Clean up this duplication between Null and Dummy public key
@CordaSerializable
@Deprecated("Has encoding format problems, consider entropyToKeyPair() instead")
class DummyPublicKey(val s: String) : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "DUMMY"
    override fun getEncoded() = s.toByteArray()
    override fun getFormat() = "ASN.1"
    override fun compareTo(other: PublicKey): Int = BigInteger(encoded).compareTo(BigInteger(other.encoded))
    override fun equals(other: Any?) = other is DummyPublicKey && other.s == s
    override fun hashCode(): Int = s.hashCode()
    override fun toString() = "PUBKEY[$s]"
}

/** A signature with a key and value of zero. Useful when you want a signature object that you know won't ever be used. */
@CordaSerializable
object NullSignature : DigitalSignature.WithKey(NullPublicKey, ByteArray(32))

/**
 * Utility to simplify the act of signing a byte array.
 * @param bytesToSign the data/message to be signed in [ByteArray] form (usually the Merkle root).
 * @return the [DigitalSignature] object on the input message.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
 * @throws InvalidKeyException if the private key is invalid.
 * @throws SignatureException if signing is not possible due to malformed data or private key.
 */
@Throws(IllegalArgumentException::class, InvalidKeyException::class, SignatureException::class)
fun PrivateKey.sign(bytesToSign: ByteArray): DigitalSignature {
    return DigitalSignature(Crypto.doSign(this, bytesToSign))
}

fun PrivateKey.sign(bytesToSign: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
    return DigitalSignature.WithKey(publicKey, this.sign(bytesToSign).bytes)
}

/**
 * Helper function to sign with a key pair.
 * @param bytesToSign the data/message to be signed in [ByteArray] form (usually the Merkle root).
 * @return the digital signature (in [ByteArray]) on the input message.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
 * @throws InvalidKeyException if the private key is invalid.
 * @throws SignatureException if signing is not possible due to malformed data or private key.
 */
@Throws(IllegalArgumentException::class, InvalidKeyException::class, SignatureException::class)
fun KeyPair.sign(bytesToSign: ByteArray) = private.sign(bytesToSign, public)
fun KeyPair.sign(bytesToSign: OpaqueBytes) = private.sign(bytesToSign.bytes, public)
fun KeyPair.sign(bytesToSign: OpaqueBytes, party: Party) = sign(bytesToSign.bytes, party)

// TODO This case will need more careful thinking, as party owningKey can be a CompositeKey. One way of doing that is
//  implementation of CompositeSignature.
@Throws(InvalidKeyException::class)
fun KeyPair.sign(bytesToSign: ByteArray, party: Party): DigitalSignature.LegallyIdentifiable {
    // Quick workaround when we have CompositeKey as Party owningKey.
    if (party.owningKey is CompositeKey) throw InvalidKeyException("Signing for parties with CompositeKey not supported.")
    val sig = sign(bytesToSign)
    return DigitalSignature.LegallyIdentifiable(party, sig.bytes)
}

/**
 * Utility to simplify the act of verifying a signature.
 *
 * @throws InvalidKeyException if the key to verify the signature with is not valid (i.e. wrong key type for the
 * signature).
 * @throws SignatureException if the signature is invalid (i.e. damaged), or does not match the key (incorrect).
 * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
 */
// TODO: SignatureException should be used only for a damaged signature, as per `java.security.Signature.verify()`,
@Throws(SignatureException::class, IllegalArgumentException::class, InvalidKeyException::class)
fun PublicKey.verify(content: ByteArray, signature: DigitalSignature) = Crypto.doVerify(this, signature.bytes, content)

/**
 * Utility to simplify the act of verifying a signature. In comparison to [verify] if the key and signature
 * do not match it returns false rather than throwing an exception. Normally you should use the function which throws,
 * as it avoids the risk of failing to test the result, but this is for uses such as [java.security.Signature.verify]
 * implementations.
 *
 * @throws InvalidKeyException if the key to verify the signature with is not valid (i.e. wrong key type for the
 * signature).
 * @throws SignatureException if the signature is invalid (i.e. damaged).
 * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
 * @return whether the signature is correct for this key.
 */
@Throws(IllegalStateException::class, SignatureException::class, IllegalArgumentException::class)
fun PublicKey.isValid(content: ByteArray, signature: DigitalSignature) : Boolean {
    if (this is CompositeKey)
        throw IllegalStateException("Verification of CompositeKey signatures currently not supported.") // TODO CompositeSignature verification.
    return Crypto.isValid(this, signature.bytes, content)
}

/** Render a public key to its hash (in Base58) of its serialised form using the DL prefix. */
fun PublicKey.toStringShort(): String  = "DL" + this.toSHA256Bytes().toBase58()

val PublicKey.keys: Set<PublicKey> get() {
    return if (this is CompositeKey) this.leafKeys
    else setOf(this)
}

fun PublicKey.isFulfilledBy(otherKey: PublicKey): Boolean  = isFulfilledBy(setOf(otherKey))
fun PublicKey.isFulfilledBy(otherKeys: Iterable<PublicKey>): Boolean {
    return if (this is CompositeKey) this.isFulfilledBy(otherKeys)
    else this in otherKeys
}

/** Checks whether any of the given [keys] matches a leaf on the CompositeKey tree or a single PublicKey */
fun PublicKey.containsAny(otherKeys: Iterable<PublicKey>): Boolean {
    return if (this is CompositeKey) keys.intersect(otherKeys).isNotEmpty()
    else this in otherKeys
}

/** Returns the set of all [PublicKey]s of the signatures */
fun Iterable<DigitalSignature.WithKey>.byKeys() = map { it.by }.toSet()

// Allow Kotlin destructuring:    val (private, public) = keyPair
operator fun KeyPair.component1(): PrivateKey = this.private

operator fun KeyPair.component2(): PublicKey = this.public

/** A simple wrapper that will make it easier to swap out the EC algorithm we use in future */
fun generateKeyPair(): KeyPair = Crypto.generateKeyPair()

/**
 * Returns a key pair derived from the given private key entropy. This is useful for unit tests and other cases where
 * you want hard-coded private keys.
 * This currently works for the default signature scheme EdDSA ed25519 only.
 */
fun entropyToKeyPair(entropy: BigInteger): KeyPair = Crypto.deriveKeyPairFromEntropy(entropy)

/**
 * Helper function for signing.
 * @param metaData tha attached MetaData object.
 * @return a [TransactionSignature ] object.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
 * @throws InvalidKeyException if the private key is invalid.
 * @throws SignatureException if signing is not possible due to malformed data or private key.
 */
@Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
fun PrivateKey.sign(metaData: MetaData): TransactionSignature = Crypto.doSign(this, metaData)

/**
 * Helper function to verify a signature.
 * @param signatureData the signature on a message.
 * @param clearData the clear data/message that was signed (usually the Merkle root).
 * @throws InvalidKeyException if the key is invalid.
 * @throws SignatureException if this signatureData object is not initialized properly,
 * the passed-in signatureData is improperly encoded or of the wrong type,
 * if this signatureData algorithm is unable to process the input data provided, etc.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
 */
@Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
fun PublicKey.verify(signatureData: ByteArray, clearData: ByteArray): Boolean = Crypto.doVerify(this, signatureData, clearData)

/**
 * Helper function to verify a metadata attached signature. It is noted that the transactionSignature contains
 * signatureData and a [MetaData] object that contains the signer's public key and the transaction's Merkle root.
 * @param transactionSignature a [TransactionSignature] object that .
 * @throws InvalidKeyException if the key is invalid.
 * @throws SignatureException if this signatureData object is not initialized properly,
 * the passed-in signatureData is improperly encoded or of the wrong type,
 * if this signatureData algorithm is unable to process the input data provided, etc.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
 */
@Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
fun PublicKey.verify(transactionSignature: TransactionSignature): Boolean {
    return Crypto.doVerify(this, transactionSignature)
}

/**
 * Helper function for the signers to verify their own signature.
 * @param signatureData the signature on a message.
 * @param clearData the clear data/message that was signed (usually the Merkle root).
 * @throws InvalidKeyException if the key is invalid.
 * @throws SignatureException if this signatureData object is not initialized properly,
 * the passed-in signatureData is improperly encoded or of the wrong type,
 * if this signatureData algorithm is unable to process the input data provided, etc.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
 */
@Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
fun KeyPair.verify(signatureData: ByteArray, clearData: ByteArray): Boolean = Crypto.doVerify(this.public, signatureData, clearData)

/**
 * Generate a securely random [ByteArray] of requested number of bytes. Usually used for seeds, nonces and keys.
 * @param numOfBytes how many random bytes to output.
 * @return a random [ByteArray].
 * @throws NoSuchAlgorithmException thrown if "NativePRNGNonBlocking" is not supported on the JVM
 * or if no strong SecureRandom implementations are available or if Security.getProperty("securerandom.strongAlgorithms") is null or empty,
 * which should never happen and suggests an unusual JVM or non-standard Java library.
 */
@Throws(NoSuchAlgorithmException::class)
fun secureRandomBytes(numOfBytes: Int): ByteArray {
    return newSecureRandom().generateSeed(numOfBytes)
}

/**
 * Get an instance of [SecureRandom] to avoid blocking, due to waiting for additional entropy, when possible.
 * In this version, the NativePRNGNonBlocking is exclusively used on Linux OS to utilize dev/urandom because in high traffic
 * /dev/random may wait for a certain amount of "noise" to be generated on the host machine before returning a result.
 *
 * On Solaris, Linux, and OS X, if the entropy gathering device in java.security is set to file:/dev/urandom
 * or file:/dev/random, then NativePRNG is preferred to SHA1PRNG. Otherwise, SHA1PRNG is preferred.
 * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SecureRandomImp">SecureRandom Implementation</a>.
 *
 * If both dev/random and dev/urandom are available, then dev/random is only preferred over dev/urandom during VM boot
 * where it may be possible that OS didn't yet collect enough entropy to fill the randomness pool for the 1st time.
 * @see <a href="http://www.2uo.de/myths-about-urandom/">Myths about urandom</a> for a more descriptive explanation on /dev/random Vs /dev/urandom.
 * TODO: check default settings per OS and random/urandom availability.
 * @return a [SecureRandom] object.
 * @throws NoSuchAlgorithmException thrown if "NativePRNGNonBlocking" is not supported on the JVM
 * or if no strong SecureRandom implementations are available or if Security.getProperty("securerandom.strongAlgorithms") is null or empty,
 * which should never happen and suggests an unusual JVM or non-standard Java library.
 */
@Throws(NoSuchAlgorithmException::class)
fun newSecureRandom(): SecureRandom {
    if (System.getProperty("os.name") == "Linux") {
        return SecureRandom.getInstance("NativePRNGNonBlocking")
    } else {
        return SecureRandom.getInstanceStrong()
    }
}
