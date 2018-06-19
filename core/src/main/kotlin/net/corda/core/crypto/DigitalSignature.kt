package net.corda.core.crypto

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.OpaqueBytes
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException

// TODO: Is there a use-case for bare [DigitalSignature], or is everything a [DigitalSignature.WithKey]? If there's no
//       actual use-case, we should merge the with key version into the parent class. In that case [CompositeSignatureWithKeys]
//       should be renamed to match.
/** A wrapper around a digital signature. */
@CordaSerializable
open class DigitalSignature(bits: ByteArray) : OpaqueBytes(bits) {
    /** A digital signature that identifies who the public key is owned by. */
    open class WithKey(val by: PublicKey, bits: ByteArray) : DigitalSignature(bits) {
        /**
         * Utility to simplify the act of verifying a signature.
         *
         * @throws InvalidKeyException if the key to verify the signature with is not valid (i.e. wrong key type for the
         * signature).
         * @throws SignatureException if the signature is invalid (i.e. damaged), or does not match the key (incorrect).
         */
        @Throws(InvalidKeyException::class, SignatureException::class)
        fun verify(content: ByteArray) = by.verify(content, this)
        /**
         * Utility to simplify the act of verifying a signature.
         *
         * @throws InvalidKeyException if the key to verify the signature with is not valid (i.e. wrong key type for the
         * signature).
         * @throws SignatureException if the signature is invalid (i.e. damaged), or does not match the key (incorrect).
         */
        @Throws(InvalidKeyException::class, SignatureException::class)
        fun verify(content: OpaqueBytes) = by.verify(content.bytes, this)
        /**
         * Utility to simplify the act of verifying a signature. In comparison to [verify] doesn't throw an
         * exception, making it more suitable where a boolean is required, but normally you should use the function
         * which throws, as it avoids the risk of failing to test the result.
         *
         * @throws InvalidKeyException if the key to verify the signature with is not valid (i.e. wrong key type for the
         * signature).
         * @throws SignatureException if the signature is invalid (i.e. damaged).
         * @return whether the signature is correct for this key.
         */
        @Throws(InvalidKeyException::class, SignatureException::class)
        fun isValid(content: ByteArray) = by.isValid(content, this)
    }

    // TODO: consider removing this as whoever needs to identify the signer should be able to derive it from the public key
    class LegallyIdentifiable(val signer: Party, bits: ByteArray) : WithKey(signer.owningKey, bits)
}
