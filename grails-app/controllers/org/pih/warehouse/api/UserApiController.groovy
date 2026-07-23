/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/
package org.pih.warehouse.api

import grails.converters.JSON
import grails.validation.ValidationException
import org.apache.http.auth.AuthenticationException
import org.hibernate.ObjectNotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus

import org.pih.warehouse.core.Location
import org.pih.warehouse.core.LocationRole
import org.pih.warehouse.core.Role
import org.pih.warehouse.core.User
import org.pih.warehouse.core.UserDataService

class UserApiController {

    def userService
    def mailService
    UserDataService userGormService

    def list() {
        Map listParams = [
                max   : Math.min(params.max ? params.int('max') : 10, 100),
                offset: params.offset ? params.int('offset') : 0,
                status: params.status,
        ]
        String sort = params.sort in ['username', 'firstName', 'lastName', 'email', 'active', 'lastLoginDate'] ? params.sort : null
        if (sort) {
            listParams.sort = sort
            listParams.order = params.order == 'desc' ? 'desc' : 'asc'
        }
        String query = params.q ? "%" + params.q + "%" : ""
        def results = userService.findUsers(query, listParams)
        render([data: results.collect { toListJson(it) }, totalCount: results.totalCount] as JSON)
    }

    def read() {
        User user = User.get(params.id)
        if (!user) {
            throw new ObjectNotFoundException(params.id, User.class.toString())
        }
        render([data: toDetailsJson(user)] as JSON)
    }

    def update() {
        User user = User.get(params.id)
        if (!user) {
            throw new ObjectNotFoundException(params.id, User.class.toString())
        }
        boolean rolesProvided = params.containsKey('roles')
        List<String> requestedRoleIds = extractRoleIds(params)
        // An explicitly-provided empty roles list clears all roles, mirroring the
        // legacy edit screen's "No access" option (a 'null' id that resolves to no roles).
        if (rolesProvided && !requestedRoleIds) {
            requestedRoleIds = ['null']
        }
        Map updateParams = [:]
        ['username', 'firstName', 'lastName', 'email', 'locale', 'timezone'].each { key ->
            if (params.containsKey(key)) {
                updateParams[key] = params[key]
            }
        }
        if (params.containsKey('active')) {
            updateParams.active = params.boolean('active')
        }
        if (params.containsKey('rememberLastLocation')) {
            updateParams.rememberLastLocation = params.boolean('rememberLastLocation')
        }
        if (params.warehouse instanceof Map || params.containsKey('warehouse.id')) {
            String warehouseId = params.warehouse instanceof Map ? params.warehouse.id : params['warehouse.id']
            updateParams.warehouse = warehouseId ? Location.get(warehouseId) : null
        }
        try {
            user = userService.updateUser(params.id, session.user.id, requestedRoleIds, updateParams)
            // Update session data if the user is editing their own profile
            if (session.user.id == user?.id) {
                session.user = User.get(user?.id)
                if (updateParams.timezone) {
                    session.timezone = TimeZone.getTimeZone(updateParams.timezone as String)
                }
            }
            render([data: toDetailsJson(user)] as JSON)
        } catch (ValidationException e) {
            renderValidationErrors(e)
        }
    }

    def changePassword() {
        User user = userGormService.get(params.id)
        if (!user) {
            throw new ObjectNotFoundException(params.id, User.class.toString())
        }
        try {
            userService.changePassword(user, params.password as String, params.passwordConfirm as String)
            render([data: toDetailsJson(User.get(params.id))] as JSON)
        } catch (ValidationException e) {
            renderValidationErrors(e)
        } catch (AuthenticationException e) {
            response.status = HttpStatus.FORBIDDEN.value()
            render([errorCode: HttpStatus.FORBIDDEN.value(), errorMessage: e.message] as JSON)
        }
    }

    def delete() {
        User user = userGormService.get(params.id)
        if (!user) {
            throw new ObjectNotFoundException(params.id, User.class.toString())
        }
        if (user.id == session?.user?.id) {
            String message = "${warehouse.message(code: 'default.cannot.delete.self.message', args: [warehouse.message(code: 'user.label'), params.id])}"
            response.status = HttpStatus.BAD_REQUEST.value()
            render([errorCode: HttpStatus.BAD_REQUEST.value(), errorMessage: message] as JSON)
            return
        }
        try {
            userGormService.delete(user.id)
        } catch (DataIntegrityViolationException ignored) {
            String message = "${warehouse.message(code: 'default.not.deleted.message', args: [warehouse.message(code: 'user.label'), params.id])}"
            response.status = HttpStatus.BAD_REQUEST.value()
            render([errorCode: HttpStatus.BAD_REQUEST.value(), errorMessage: message] as JSON)
            return
        }
        render status: HttpStatus.NO_CONTENT.value()
    }

    def saveLocationRole() {
        User user = User.get(params.id)
        if (!user) {
            throw new ObjectNotFoundException(params.id, User.class.toString())
        }
        Location location = params.location instanceof Map ? Location.get(params.location.id) : Location.get(params['location.id'])
        List<String> roleIds = extractRoleIds(params, 'role')
        List<Role> roles = roleIds.collect { Role.get(it) }.findAll { it }
        try {
            userService.saveLocationRole(location, null, roles, user, session.user.id)
        } catch (ValidationException e) {
            response.status = HttpStatus.BAD_REQUEST.value()
            render([errorCode: HttpStatus.BAD_REQUEST.value(), errorMessage: e.message] as JSON)
            return
        }
        render([data: toDetailsJson(User.get(params.id))] as JSON)
    }

    def deleteLocationRole() {
        User user = User.get(params.id)
        if (!user) {
            throw new ObjectNotFoundException(params.id, User.class.toString())
        }
        LocationRole locationRole = LocationRole.get(params.locationRoleId)
        if (!locationRole || locationRole.user?.id != user.id) {
            throw new ObjectNotFoundException(params.locationRoleId as String, LocationRole.class.toString())
        }
        try {
            userService.deleteLocationRole(locationRole, session.user.id)
        } catch (ValidationException e) {
            response.status = HttpStatus.BAD_REQUEST.value()
            render([errorCode: HttpStatus.BAD_REQUEST.value(), errorMessage: e.message] as JSON)
            return
        }
        render([data: toDetailsJson(User.get(params.id))] as JSON)
    }

    def uploadPhoto() {
        User user = User.get(params.id)
        if (!user) {
            throw new ObjectNotFoundException(params.id, User.class.toString())
        }
        def photo = request.getFile("photo")
        def okcontents = ['image/png', 'image/jpeg', 'image/gif']
        if (!photo || !okcontents.contains(photo.getContentType())) {
            String message = "Photo must be one of: ${okcontents}"
            response.status = HttpStatus.BAD_REQUEST.value()
            render([errorCode: HttpStatus.BAD_REQUEST.value(), errorMessage: message] as JSON)
            return
        }
        if (photo.empty || photo.size >= 1024 * 1000) {
            String message = "${warehouse.message(code: 'user.photoTooLarge.message', args: [warehouse.message(code: 'user.label'), user.id])}"
            response.status = HttpStatus.BAD_REQUEST.value()
            render([errorCode: HttpStatus.BAD_REQUEST.value(), errorMessage: message] as JSON)
            return
        }
        user.photo = photo.bytes
        user.save(flush: true)
        sendUserPhotoChanged(user)
        render([data: toDetailsJson(user)] as JSON)
    }

    private void sendUserPhotoChanged(User user) {
        try {
            String subject = "${warehouse.message(code: 'email.userPhotoChanged.message', args: [user?.email])}"
            String body = "${g.render(template: '/email/userPhotoChanged', model: [userInstance: user])}"
            mailService.sendHtmlMailWithAttachment(user, subject, body, user.photo, "photo.png", "image/png")
        }
        catch (Exception e) {
            log.warn("Unable to send photo-changed email to ${user?.email}: ${e.message}")
        }
    }

    private void renderValidationErrors(ValidationException e) {
        List<String> errorMessages = e.errors.allErrors.collect { error ->
            g.message(error: error) as String
        }
        response.status = HttpStatus.BAD_REQUEST.value()
        render([
                errorCode    : HttpStatus.BAD_REQUEST.value(),
                errorMessage : errorMessages.join('; '),
                errorMessages: errorMessages,
        ] as JSON)
    }

    /**
     * Role IDs must never reach GORM binding directly; see UserService.updateUser.
     */
    private static List<String> extractRoleIds(Map params, String key = 'roles') {
        def value = params[key]
        params.keySet().removeAll { it?.toString()?.startsWith(key) }
        if (value == null) {
            return []
        }
        List ids = value instanceof List ? value : [value]
        return ids.collect { it instanceof Map ? it.id as String : it as String }.findAll { it }
    }

    private static Map toListJson(User user) {
        return [
                id           : user.id,
                username     : user.username,
                name         : user.name,
                email        : user.email,
                active       : user.active,
                locale       : user.locale?.toString(),
                localeDisplayName: user.locale?.displayName,
                roles        : user.roles?.sort()?.collect { it.description ?: it.roleType?.name() }?.join(', '),
                lastLoginDate: user.lastLoginDate,
        ]
    }

    private static Map toDetailsJson(User user) {
        return [
                id                  : user.id,
                username            : user.username,
                firstName           : user.firstName,
                lastName            : user.lastName,
                name                : user.name,
                email               : user.email,
                active              : user.active,
                locale              : user.locale?.toString(),
                localeDisplayName   : user.locale?.displayName,
                timezone            : user.timezone,
                rememberLastLocation: user.rememberLastLocation,
                warehouse           : user.warehouse ? [id: user.warehouse.id, name: user.warehouse.name] : null,
                roles               : user.roles?.sort()?.collect { Role role ->
                    [id: role.id, roleType: role.roleType?.name(), description: role.description ?: role.roleType?.name()]
                } ?: [],
                locationRoles       : user.locationRoles?.sort { a, b ->
                    (a.location?.name ?: '') <=> (b.location?.name ?: '') ?: (a.role?.roleType?.sortOrder ?: 0) <=> (b.role?.roleType?.sortOrder ?: 0)
                }?.collect { LocationRole locationRole ->
                    [
                            id           : locationRole.id,
                            location     : [
                                    id           : locationRole.location?.id,
                                    name         : locationRole.location?.name,
                                    locationGroup: locationRole.location?.locationGroup?.name,
                                    locationType : locationRole.location?.locationType?.name,
                            ],
                            role         : [
                                    id         : locationRole.role?.id,
                                    roleType   : locationRole.role?.roleType?.name(),
                                    description: locationRole.role?.description ?: locationRole.role?.roleType?.name(),
                            ],
                            highestActive: locationRole?.role in locationRole?.user?.getHighestRole(locationRole.location),
                    ]
                } ?: [],
                lastLoginDate       : user.lastLoginDate,
                dateCreated         : user.dateCreated,
                lastUpdated         : user.lastUpdated,
                version             : user.version,
        ]
    }
}
