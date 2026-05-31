package com.securechat.securemessaging.security;

import javax.crypto.KeyAgreement;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Diffie-Hellman key exchange utility.
 *
 * Generates 2048-bit DH key pairs and derives a 16-byte (128-bit) shared
 * secret suitable for use as an AES key.
 */
public class DHUtil {

    /** Generate a 2048-bit DH key pair. */
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    /** Encode a public key to a Base64 string for storage / transmission. */
    public static String publicKeyToString(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** Encode a private key to a Base64 string for storage. */
    public static String privateKeyToString(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** Decode a Base64 string back to a DH PublicKey. */
    public static PublicKey stringToPublicKey(String keyStr) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(keyStr);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
        return KeyFactory.getInstance("DH").generatePublic(spec);
    }

    /** Decode a Base64 string back to a DH PrivateKey. */
    public static PrivateKey stringToPrivateKey(String keyStr) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(keyStr);
        java.security.spec.PKCS8EncodedKeySpec spec =
                new java.security.spec.PKCS8EncodedKeySpec(bytes);
        return KeyFactory.getInstance("DH").generatePrivate(spec);
    }

    /**
     * Perform DH key agreement and return the first 16 bytes of the shared
     * secret as a 128-bit AES key.
     */
    public static byte[] generateSharedSecret(PrivateKey privateKey,
                                               PublicKey otherPublicKey) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("DH");
        agreement.init(privateKey);
        agreement.doPhase(otherPublicKey, true);
        byte[] fullSecret = agreement.generateSecret();
        // Use first 16 bytes → 128-bit AES key
        return Arrays.copyOf(fullSecret, 16);
    }
}
