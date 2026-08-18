/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/
package org.pih.warehouse.core

import groovy.util.logging.Slf4j
import org.apache.commons.codec.binary.Base64

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Salted, adaptive password hashing (PBKDF2-HMAC-SHA256).
 *
 * Hashed passwords are stored as {@code pbkdf2-sha256$<iterations>$<salt>$<key>},
 * where salt and key are Base64-encoded. Stored values without that prefix are
 * legacy unsalted SHA-1 digests: they are still verified, so existing accounts
 * keep working, and {@link #needsRehash} asks the caller to upgrade them the
 * next time the password is known (i.e. on a successful login).
 */
@Slf4j
class PasswordHasher {

    static final String ALGORITHM = "PBKDF2WithHmacSHA256"
    static final String PREFIX = "pbkdf2-sha256"
    static final String SEPARATOR = '$'

    /** OWASP-recommended work factor for PBKDF2-HMAC-SHA256. */
    static final int ITERATIONS = 210000
    static final int SALT_LENGTH = 16
    static final int KEY_LENGTH = 256

    private static final SecureRandom RANDOM = new SecureRandom()

    /**
     * Hash a cleartext password with a freshly generated salt. Two calls with the
     * same password return different values, so encoded passwords must always be
     * compared using {@link #matches} rather than string equality.
     */
    static String hash(String rawPassword) {
        if (rawPassword == null) {
            return null
        }
        byte[] salt = new byte[SALT_LENGTH]
        RANDOM.nextBytes(salt)
        byte[] key = pbkdf2(rawPassword, salt, ITERATIONS, KEY_LENGTH)
        return [PREFIX, ITERATIONS as String, toBase64(salt), toBase64(key)].join(SEPARATOR)
    }

    /**
     * Verify a cleartext password against a stored password.
     *
     * The stored value is never accepted as the password itself: submitting a
     * hash read out of the database does not authenticate anyone.
     */
    static boolean matches(String rawPassword, String storedPassword) {
        if (!rawPassword || !storedPassword) {
            return false
        }
        if (!isHashed(storedPassword)) {
            return isEqual(legacyHash(rawPassword), storedPassword)
        }
        List<String> parts = storedPassword.tokenize(SEPARATOR)
        if (parts.size() != 4) {
            return false
        }
        try {
            int iterations = parts[1] as int
            byte[] salt = fromBase64(parts[2])
            byte[] expectedKey = fromBase64(parts[3])
            if (iterations <= 0 || salt.length == 0 || expectedKey.length == 0) {
                return false
            }
            byte[] actualKey = pbkdf2(rawPassword, salt, iterations, expectedKey.length * 8)
            return MessageDigest.isEqual(expectedKey, actualKey)
        } catch (Exception e) {
            log.warn("Unable to verify stored password: ${e.message}")
            return false
        }
    }

    /**
     * Whether a stored password should be re-hashed with the current algorithm
     * and work factor. True for legacy SHA digests and for weaker parameters.
     */
    static boolean needsRehash(String storedPassword) {
        if (!storedPassword || !isHashed(storedPassword)) {
            return true
        }
        List<String> parts = storedPassword.tokenize(SEPARATOR)
        if (parts.size() != 4) {
            return true
        }
        try {
            return (parts[1] as int) < ITERATIONS
        } catch (NumberFormatException ignored) {
            return true
        }
    }

    static boolean isHashed(String storedPassword) {
        return storedPassword?.startsWith(PREFIX + SEPARATOR)
    }

    /**
     * The pre-existing, unsalted SHA-1 digest. Only used to verify passwords that
     * were stored before the migration to PBKDF2; never used to store new ones.
     */
    static String legacyHash(String rawPassword) {
        MessageDigest md = MessageDigest.getInstance("SHA")
        md.update(rawPassword.getBytes("UTF-8"))
        return new String(Base64.encodeBase64(md.digest()))
    }

    private static byte[] pbkdf2(String rawPassword, byte[] salt, int iterations, int keyLength) {
        PBEKeySpec keySpec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, keyLength)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    private static boolean isEqual(String left, String right) {
        return MessageDigest.isEqual(left.getBytes("UTF-8"), right.getBytes("UTF-8"))
    }

    private static String toBase64(byte[] bytes) {
        return new String(Base64.encodeBase64(bytes), "UTF-8")
    }

    private static byte[] fromBase64(String value) {
        return Base64.decodeBase64(value.getBytes("UTF-8"))
    }
}
