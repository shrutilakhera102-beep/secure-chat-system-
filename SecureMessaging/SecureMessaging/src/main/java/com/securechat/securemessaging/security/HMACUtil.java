package com.securechat.securemessaging.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * HMAC-SHA256 utility for message integrity verification.
 *
 * Uses time-constant comparison (MessageDigest.isEqual) to prevent
 * timing-based side-channel attacks.
 */
public class HMACUtil {

    private static final String HMAC_ALGO = "HmacSHA256";

    /**
     * Generate a Base64-encoded HMAC-SHA256 for the given data and key.
     *
     * @param data the message to authenticate
     * @param key  raw key bytes
     * @return Base64-encoded HMAC string
     */
    public static String generateHMAC(String data, byte[] key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key, HMAC_ALGO);
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(data.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    /**
     * Verify a received HMAC against the expected value using time-constant comparison.
     *
     * @param data         the original message
     * @param key          raw key bytes
     * @param receivedHmac the HMAC to verify (Base64-encoded)
     * @return true if the HMAC is valid
     */
    public static boolean verifyHMAC(String data, byte[] key, String receivedHmac) throws Exception {
        String calculated = generateHMAC(data, key);
        // Time-constant comparison — prevents timing attacks
        return MessageDigest.isEqual(
                Base64.getDecoder().decode(calculated),
                Base64.getDecoder().decode(receivedHmac)
        );
    }
}
