package com.securechat.securemessaging.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption utility.
 *
 * Format of encrypted output (Base64-encoded):
 *   [ 12-byte IV ][ ciphertext + 16-byte GCM auth tag ]
 *
 * The IV is randomly generated per message and prepended to the ciphertext
 * so it can be recovered during decryption without storing it separately.
 */
public class AESUtil {

    private static final String ALGO          = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LENGTH = 12;   // 96-bit IV recommended for GCM
    private static final int    GCM_TAG_BITS  = 128;  // 128-bit authentication tag

    /**
     * Encrypt plaintext with the given 16-byte (128-bit) AES key.
     *
     * @param data plaintext string
     * @param key  16-byte raw key bytes
     * @return Base64-encoded string: IV (12 bytes) + ciphertext
     */
    public static String encrypt(String data, byte[] key) throws Exception {
        SecretKey secretKey = new SecretKeySpec(key, "AES");

        // Generate a fresh random IV for every message
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] ciphertext = cipher.doFinal(data.getBytes("UTF-8"));

        // Prepend IV to ciphertext
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypt a Base64-encoded string produced by {@link #encrypt}.
     *
     * @param encryptedData Base64-encoded IV + ciphertext
     * @param key           16-byte raw key bytes
     * @return original plaintext string
     */
    public static String decrypt(String encryptedData, byte[] key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedData);

        // Extract IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

        // Extract ciphertext
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        SecretKey secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

        return new String(cipher.doFinal(ciphertext), "UTF-8");
    }
}
