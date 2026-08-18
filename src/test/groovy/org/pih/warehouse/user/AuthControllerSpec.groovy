package org.pih.warehouse.user

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

import org.pih.warehouse.PasswordCodec
import org.pih.warehouse.core.Role
import org.pih.warehouse.core.RoleType
import org.pih.warehouse.core.User
import org.pih.warehouse.core.UserService

class AuthControllerSpec extends Specification implements ControllerUnitTest<AuthController>, DataTest {

    Class[] getDomainClassesToMock() {
        [User, Role]
    }

    void setup() {
        mockCodec(PasswordCodec)
        controller.userService = Stub(UserService)
        controller.metaClass.warehouse = new Expando(message: { args -> "message" })
        config.openboxes.signup.enabled = true
        config.openboxes.signup.recaptcha.enabled = false
    }

    void "handleSignup should not create an account when signup is disabled"() {
        given:
        config.openboxes.signup.enabled = false

        when:
        request.method = "POST"
        params.firstName = "John"
        params.lastName = "Doe"
        params.email = "john.doe@openboxes.com"
        params.password = "Password123"
        params.passwordConfirm = "Password123"
        controller.handleSignup()

        then:
        response.redirectedUrl == '/auth/login'
        User.count() == 0
    }

    void "handleSignup should not bind roles or the active flag from request params"() {
        given:
        Role superuser = new Role(name: "Superuser", roleType: RoleType.ROLE_SUPERUSER)
                .save(flush: true, failOnError: true)

        when:
        request.method = "POST"
        params.firstName = "John"
        params.lastName = "Doe"
        params.email = "john.doe@openboxes.com"
        params.password = "Password123"
        params.passwordConfirm = "Password123"
        params."roles[0].id" = superuser.id
        params.active = "true"
        controller.handleSignup()

        then:
        User.count() == 1
        User createdUser = User.list().first()
        !createdUser.roles
        !createdUser.active
    }
}
