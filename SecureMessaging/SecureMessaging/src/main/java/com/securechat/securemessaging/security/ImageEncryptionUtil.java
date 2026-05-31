package com.securechat.securemessaging.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM image encryption utility.
 *
 * Format of encrypted output (Base64-encoded):
 *   [ 12-byte IV ][ ciphertext + 16-byte GCM auth tag ]
 */
public class ImageEncryptionUtil {

    private static final String ALGO          = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LENGTH = 12;
    private static final int    GCM_TAG_BITS  = 128;

    /**
     * Encrypt raw image bytes with the given AES key.
     *
     * @param imageBytes raw image bytes
     * @param key        16-byte raw key bytes
     * @return Base64-encoded IV + ciphertext
     */
    public static String encryptImage(byte[] imageBytes, byte[] key) throws Exception {
        SecretKey secretKey = new SecretKeySpec(key, "AES");

        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] ciphertext = cipher.doFinal(imageBytes);

        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypt a Base64-encoded string produced by {@link #encryptImage}.
     *
     * @param encryptedImageData Base64-encoded IV + ciphertext
     * @param key                16-byte raw key bytes
     * @return original image bytes
     */
    public static byte[] decryptImage(String encryptedImageData, byte[] key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedImageData);

        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        SecretKey secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

        return cipher.doFinal(ciphertext);
    }
}
