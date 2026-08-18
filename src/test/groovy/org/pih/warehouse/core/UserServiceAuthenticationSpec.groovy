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

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class UserServiceAuthenticationSpec extends Specification implements ServiceUnitTest<UserService>, DataTest {

    LoginAttemptService loginAttemptService

    void setupSpec() {
        mockDomain(User)
    }

    void setup() {
        loginAttemptService = Mock(LoginAttemptService)
        service.loginAttemptService = loginAttemptService
    }

    private User createUser(String username, String storedPassword) {
        return new User(username: username, password: storedPassword, passwordConfirm: storedPassword,
                firstName: 'Test', lastName: 'User', email: "${username}@test.com", active: true)
                .save(validate: false, flush: true)
    }

    void "authenticate accepts the password of a user hashed with the current algorithm"() {
        given:
        createUser('jdoe', PasswordHasher.hash('password123'))

        when:
        boolean authenticated = service.authenticate('jdoe', 'password123', '10.0.0.1')

        then:
        authenticated
        1 * loginAttemptService.isLockedOut('jdoe', '10.0.0.1') >> false
        1 * loginAttemptService.recordSuccess('jdoe', '10.0.0.1')
    }

    void "authenticate rejects the stored password value submitted as the password"() {
        given:
        String stored = PasswordHasher.hash('password123')
        createUser('jdoe', stored)

        when: "an attacker who read the password column replays the stored value"
        boolean authenticated = service.authenticate('jdoe', stored, '10.0.0.1')

        then:
        !authenticated
        1 * loginAttemptService.isLockedOut('jdoe', '10.0.0.1') >> false
        1 * loginAttemptService.recordFailure('jdoe', '10.0.0.1')
    }

    void "authenticate rejects a wrong password and an unknown user"() {
        given:
        createUser('jdoe', PasswordHasher.hash('password123'))
        loginAttemptService.isLockedOut(_, _) >> false

        expect:
        !service.authenticate('jdoe', 'wrong', '10.0.0.1')
        !service.authenticate('nobody', 'password123', '10.0.0.1')
        !service.authenticate('jdoe', null, '10.0.0.1')
    }

    void "authenticate rejects any password while the account is locked out"() {
        given:
        createUser('jdoe', PasswordHasher.hash('password123'))

        when:
        boolean authenticated = service.authenticate('jdoe', 'password123', '10.0.0.1')

        then:
        !authenticated
        1 * loginAttemptService.isLockedOut('jdoe', '10.0.0.1') >> true
        0 * loginAttemptService.recordSuccess(_, _)
    }

    void "authenticate upgrades a legacy unsalted password on a successful login"() {
        given:
        createUser('jdoe', PasswordHasher.legacyHash('password123'))
        loginAttemptService.isLockedOut(_, _) >> false

        when:
        boolean authenticated = service.authenticate('jdoe', 'password123', '10.0.0.1')

        then:
        authenticated
        PasswordHasher.isHashed(User.findByUsername('jdoe').password)
        PasswordHasher.matches('password123', User.findByUsername('jdoe').password)

        and: "the legacy digest is no longer a valid credential"
        !service.authenticate('jdoe', PasswordHasher.legacyHash('password123'), '10.0.0.1')
    }

    void "assignPassword stores a salted hash and keeps the confirmation check meaningful"() {
        given:
        User user = new User(username: 'jdoe', firstName: 'Test', lastName: 'User', email: 'jdoe@test.com')

        when:
        service.assignPassword(user, 'password123', 'password123')

        then:
        PasswordHasher.isHashed(user.password)
        user.passwordConfirm == user.password
        PasswordHasher.matches('password123', user.password)

        when: "the confirmation does not match"
        service.assignPassword(user, 'password123', 'something-else')

        then:
        user.passwordConfirm != user.password
        !user.validate(['password'])
    }
}
