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

import grails.config.Config
import grails.core.GrailsApplication
import spock.lang.Specification

class LoginAttemptServiceSpec extends Specification {

    LoginAttemptService service = new LoginAttemptService()

    int lockoutDuration = 900000

    void setup() {
        Config config = Stub(Config) {
            getProperty("openboxes.login.throttle.maxAttemptsPerUsername", Integer, _) >> 3
            getProperty("openboxes.login.throttle.maxAttemptsPerIpAddress", Integer, _) >> 5
            getProperty("openboxes.login.throttle.lockoutDuration", Integer, _) >> { lockoutDuration }
        }
        service.grailsApplication = Stub(GrailsApplication) {
            getConfig() >> config
        }
    }

    void "an account is locked out after too many failures"() {
        expect:
        !service.isLockedOut("jdoe", "10.0.0.1")

        when:
        3.times { service.recordFailure("jdoe", "10.0.0.1") }

        then:
        service.isLockedOut("jdoe", "10.0.0.1")

        and: "the lockout follows the account, not the address it was attacked from"
        service.isLockedOut("jdoe", "10.0.0.2")
        !service.isLockedOut("someone.else", "10.0.0.2")
    }

    void "the username is matched case insensitively"() {
        when:
        3.times { service.recordFailure("JDoe", null) }

        then:
        service.isLockedOut("jdoe", null)
    }

    void "an address is locked out after too many failures spread over accounts"() {
        when:
        5.times { int i -> service.recordFailure("user${i}", "10.0.0.1") }

        then:
        service.isLockedOut("another.user", "10.0.0.1")
        !service.isLockedOut("another.user", "10.0.0.2")
    }

    void "a successful login clears the counters"() {
        given:
        2.times { service.recordFailure("jdoe", "10.0.0.1") }

        when:
        service.recordSuccess("jdoe", "10.0.0.1")
        2.times { service.recordFailure("jdoe", "10.0.0.1") }

        then:
        !service.isLockedOut("jdoe", "10.0.0.1")
    }

    void "a lockout expires"() {
        given:
        lockoutDuration = 0

        when:
        3.times { service.recordFailure("jdoe", "10.0.0.1") }
        Thread.sleep(5)

        then:
        !service.isLockedOut("jdoe", "10.0.0.1")
    }

    void "missing username and address is not tracked"() {
        when:
        5.times { service.recordFailure(null, null) }

        then:
        !service.isLockedOut(null, null)
    }
}
