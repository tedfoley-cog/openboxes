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

import grails.core.GrailsApplication

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Throttles login attempts, per account and per client address, to make online
 * password guessing and username enumeration impractical.
 *
 * Counters are held in memory (they are cheap and expire on their own), so each
 * application node throttles the traffic it sees.
 */
class LoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 100000

    GrailsApplication grailsApplication

    private final ConcurrentMap<String, FailedAttempts> failedAttempts = new ConcurrentHashMap<>()

    int getMaxAttemptsPerUsername() {
        return grailsApplication.config.getProperty("openboxes.login.throttle.maxAttemptsPerUsername", Integer, 10)
    }

    int getMaxAttemptsPerIpAddress() {
        return grailsApplication.config.getProperty("openboxes.login.throttle.maxAttemptsPerIpAddress", Integer, 50)
    }

    /** How long a locked out account (or address) stays locked out, in milliseconds. */
    int getLockoutDuration() {
        return grailsApplication.config.getProperty("openboxes.login.throttle.lockoutDuration", Integer, 900000)
    }

    boolean isLockedOut(String username, String ipAddress) {
        return isLockedOut(usernameKey(username), maxAttemptsPerUsername) ||
                isLockedOut(ipAddressKey(ipAddress), maxAttemptsPerIpAddress)
    }

    void recordFailure(String username, String ipAddress) {
        purgeExpiredAttempts()
        [usernameKey(username), ipAddressKey(ipAddress)].each { String key ->
            if (key) {
                failedAttempts.compute(key) { String ignored, FailedAttempts attempts ->
                    return (attempts == null || attempts.expired) ? new FailedAttempts() : attempts.increment()
                }
            }
        }
    }

    void recordSuccess(String username, String ipAddress) {
        [usernameKey(username), ipAddressKey(ipAddress)].each { String key ->
            if (key) {
                failedAttempts.remove(key)
            }
        }
    }

    private boolean isLockedOut(String key, int maxAttempts) {
        if (!key) {
            return false
        }
        FailedAttempts attempts = failedAttempts.get(key)
        if (attempts == null) {
            return false
        }
        if (attempts.expired) {
            failedAttempts.remove(key, attempts)
            return false
        }
        return attempts.count >= maxAttempts
    }

    private void purgeExpiredAttempts() {
        if (failedAttempts.size() < MAX_TRACKED_KEYS) {
            return
        }
        failedAttempts.entrySet().removeIf { Map.Entry<String, FailedAttempts> entry -> entry.value.expired }
    }

    private String usernameKey(String username) {
        return username ? "username:${username.toLowerCase()}" : null
    }

    private String ipAddressKey(String ipAddress) {
        return ipAddress ? "ipAddress:${ipAddress}" : null
    }

    private class FailedAttempts {

        final int count
        final long lastFailedAt

        FailedAttempts() {
            this(1, System.currentTimeMillis())
        }

        private FailedAttempts(int count, long lastFailedAt) {
            this.count = count
            this.lastFailedAt = lastFailedAt
        }

        FailedAttempts increment() {
            return new FailedAttempts(count + 1, System.currentTimeMillis())
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastFailedAt > lockoutDuration
        }
    }
}
