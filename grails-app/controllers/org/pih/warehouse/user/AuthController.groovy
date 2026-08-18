/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/
package org.pih.warehouse.user

import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import grails.validation.ValidationException
import org.pih.warehouse.auth.UserSignupEvent
import org.pih.warehouse.core.MailService
import org.pih.warehouse.core.User

class AuthController {

    MailService mailService
    def userService
    def authService
    GrailsApplication grailsApplication
    def recaptchaService
    def userAgentIdentService

    static allowedMethods = [login: "GET", doLogin: "POST", handleLogin: "POST", logout: "GET"]

    /**
     * Show index page - just a redirect to the list page.
     */
    def index() {
        redirect(action: "login", params: params)
    }

    /**
     * Checks whether there is an authenticated user in the session.
     */
    def authorized() {
        if (session.user == null) {
            flash.message = "${warehouse.message(code: 'auth.notAuthorized.message')}"
            redirect(controller: 'auth', action: 'login')
        }
    }

    /**
     * Allows user to log into the system.
     */
    def login() {
        if (session.user) {
            flash.message = "You have already logged in."
            redirect(controller: "dashboard", action: "index")
            return
        }

        if (userAgentIdentService.isMobile()) {
            redirect(controller: "mobile", action: "login")
            return
        }

        render(view: "/common/react", params: params)
    }


    /**
     * Performs the authentication logic.
     */
    def handleLogin() {
        // An unknown username and a wrong password give the same answer, so that the
        // login form cannot be used to find out which accounts exist.
        if (!userService.authenticate(params.username, params.password, request.remoteAddr)) {
            flash.message = "${warehouse.message(code: 'auth.unableToAuthenticateUser.message')}"
            redirect(action: 'login', params: [username: params.username])
            return
        }

        User userInstance = User.findByUsernameOrEmail(params.username, params.username)

        // Check if user is active -- redirect back to login page
        if (!userInstance?.active) {
            flash.message = "${warehouse.message(code: 'auth.accountRequestUnderReview.message')}"
            redirect(controller: 'auth', action: 'login')
            return
        }

        // FIXME Handle setting timezone based on configuration
        TimeZone userTimezone = TimeZone.getTimeZone("America/New_York")
        // Check for user's preferred timezone
        if (userInstance.timezone) {
            userTimezone = TimeZone.getTimeZone(userInstance.timezone)
        }
        // If there's no user preference timezone, use the browser timezone (login page sets parameter)
        else {
            String browserTimezone = request.getParameter("browserTimezone")
            if (browserTimezone != null) {
                userTimezone = TimeZone.getTimeZone(browserTimezone)
            }
        }
        session.timezone = userTimezone

        session.user = userInstance
        session.userName = userInstance?.username

        // PIMS-782 Force the user to select a warehouse each time
        if (userInstance?.warehouse && userInstance?.rememberLastLocation) {
            session.warehouse = userInstance.warehouse
        }

        if (session?.targetUri) {
            redirect(uri: session.targetUri)
            session.targetUri = null
            return
        }

        redirect(controller: 'dashboard', action: 'index')
    }


    /**
     * Allows user to log out of the system
     */
    def logout() {
        if (session.impersonateUserId) {
            session.user = User.get(session.activeUserId)
            session.impersonateUserId = null
            session.activeUserId = null
            redirect(controller: "dashboard", action: "index")
        } else {
            flash.message = "${warehouse.message(code: 'auth.logoutSuccess.message')}"
            session.invalidate()
            redirect(action: 'login')
        }
    }


    /**
     * Allow user to register a new account
     */
    def signup() {
        Boolean enabled = grailsApplication.config.openboxes.signup.enabled?:false
        if (!enabled) {
            flash.message = "Apologies, but the signup feature is disabled on your system. " +
                    "Please contact a system administrator for access."
            redirect(controller: "auth", action: "login")
            return
        }
        Boolean configured = grailsApplication.config.openboxes.signup.recaptcha.v2.secretKey?.trim()
        if (!configured) {
            flash.message = "Apologies, but reCAPTCHA is not set up on this system. " +
                    "Please contact a system administrator for access."
            redirect(controller: "auth", action: "login")
            return
        }
        render(view: "/common/react", params: params)
    }

    /**
     * Handle account registration.
     */
    @Transactional
    def handleSignup() {
        def userInstance = new User()
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            userInstance.properties = params

            if (params.password) {
                userService.assignPassword(userInstance, params.password as String, params.passwordConfirm as String)
            }
            userInstance.active = Boolean.FALSE

            // Set the email as username for backwards compatibility since we're no longer including username on signup
            userInstance.username = params.email

            // Verify recaptcha challenge response if recaptcha is enabled
            Boolean recaptchaEnabled = grailsApplication.config.openboxes.signup.recaptcha.enabled?:false
            if (recaptchaEnabled && !recaptchaService.validate(params["g-recaptcha-response"])) {
                userInstance.errors.reject("signup.recaptcha.fail.message",
                        "Nice try, robot. But your feeble attempt has failed. If you're not a robot we apologize. Please try again.")

                // Send failures to Sentry for auditing purposes
                def exception = new ValidationException("reCAPTCHA challenge failed", userInstance.errors)
            }

            // Create account
            if (!userInstance.hasErrors() && userInstance.save(flush: true)) {

                // Attempt to add default roles to user instance
                userService.assignDefaultRoles(userInstance)

                // Publish event to trigger email notifications
                grailsApplication.mainContext.publishEvent(new UserSignupEvent(userInstance, params.additionalQuestions))

            } else {
                // If there's an error, redirect back to the (React) signup screen
                flash.message = userInstance.errors.allErrors ?
                        "${warehouse.message(code: 'default.errors.summary.message', default: 'Please correct the errors below and try again.')}" : null
                redirect(action: "signup")
                return
            }
        }

        flash.message = "${warehouse.message(code: 'default.created.message', args: [warehouse.message(code: 'user.label'), userInstance.email])}"
        redirect(action: "login")
    }


    def renderAccountCreatedEmail() {
        def userInstance = User.get(params.id)
        render(template: "/email/userAccountCreated", model: [userInstance: userInstance])
    }

    def renderAccountConfirmedEmail() {
        def userInstance = User.get(params.id)
        render(template: "/email/userAccountConfirmed", model: [userInstance: userInstance])
    }
}
