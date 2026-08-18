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

import spock.lang.Specification

class PasswordHasherSpec extends Specification {

    void "hash salts every password"() {
        when:
        String first = PasswordHasher.hash("password123")
        String second = PasswordHasher.hash("password123")

        then:
        first != second
        first.startsWith("pbkdf2-sha256\$${PasswordHasher.ITERATIONS}\$")
        !first.contains("password123")
    }

    void "matches accepts the password and rejects anything else"() {
        given:
        String stored = PasswordHasher.hash("password123")

        expect:
        PasswordHasher.matches("password123", stored)
        !PasswordHasher.matches("Password123", stored)
        !PasswordHasher.matches("", stored)
        !PasswordHasher.matches(null, stored)
    }

    void "matches rejects the stored hash submitted as the password"() {
        given:
        String stored = PasswordHasher.hash("password123")

        expect: "reading the password column is not enough to log in"
        !PasswordHasher.matches(stored, stored)
        !PasswordHasher.matches(PasswordHasher.legacyHash("password123"), stored)
    }

    void "matches verifies legacy unsalted passwords but rejects the legacy digest as a password"() {
        given:
        String legacy = PasswordHasher.legacyHash("password123")

        expect:
        PasswordHasher.matches("password123", legacy)
        !PasswordHasher.matches("wrong", legacy)
        !PasswordHasher.matches(legacy, legacy)
    }

    void "matches fails safely on malformed stored values"() {
        expect:
        !PasswordHasher.matches("password123", storedPassword)

        where:
        storedPassword << [
                null,
                "",
                'pbkdf2-sha256$210000$notbase64',
                'pbkdf2-sha256$notanumber$c2FsdA==$a2V5',
                'pbkdf2-sha256$0$c2FsdA==$a2V5',
                'pbkdf2-sha256$210000$$',
        ]
    }

    void "needsRehash asks for an upgrade of legacy and weaker hashes"() {
        expect:
        PasswordHasher.needsRehash(PasswordHasher.legacyHash("password123"))
        PasswordHasher.needsRehash("password123")
        PasswordHasher.needsRehash(null)
        PasswordHasher.needsRehash('pbkdf2-sha256$1000$c2FsdA==$a2V5')
        !PasswordHasher.needsRehash(PasswordHasher.hash("password123"))
    }
}
