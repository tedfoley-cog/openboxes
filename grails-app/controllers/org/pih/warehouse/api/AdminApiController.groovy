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
import grails.core.GrailsApplication
import org.pih.warehouse.admin.UpgradeCommand

class AdminApiController {

    GrailsApplication grailsApplication

    def status() {
        def pluginManager = grailsApplication.mainContext.getBean('pluginManager')
        render([data: [
                appVersion     : grailsApplication.metadata.getProperty('info.app.version'),
                grailsVersion  : grailsApplication.metadata.getProperty('info.app.grailsVersion'),
                jvmVersion     : System.getProperty('java.version'),
                controllerCount: grailsApplication.controllerClasses.size(),
                domainCount    : grailsApplication.domainClasses.size(),
                serviceCount   : grailsApplication.serviceClasses.size(),
                tagLibCount    : grailsApplication.tagLibClasses.size(),
                plugins        : pluginManager.allPlugins.collect {
                    [name: it.name, version: it.version?.toString()]
                },
                controllers    : grailsApplication.controllerClasses.sort { it.logicalPropertyName }.collect {
                    [logicalName: it.logicalPropertyName, className: it.name]
                },
        ]] as JSON)
    }

    def upgrade() {
        render([data: upgradeState(session.command as UpgradeCommand)] as JSON)
    }

    def upgradeDownload() {
        UpgradeCommand command = new UpgradeCommand(remoteWebArchiveUrl: request.JSON.remoteWebArchiveUrl)
        if (command.remoteWebArchiveUrl) {
            session.command = command
            session.command.future = null
            session.command.localWebArchive = new File("warehouse.war")
            render([data: upgradeState(session.command as UpgradeCommand),
                    message: "Attempting to download '${command.remoteWebArchiveUrl}' to '${command.localWebArchive?.absolutePath}'".toString()] as JSON)
            return
        }
        response.status = 400
        render([errorCode: 400, errorMessage: "Please enter valid web archive url"] as JSON)
    }

    def upgradeDeploy() {
        UpgradeCommand command = session.command as UpgradeCommand
        if (!command?.localWebArchive) {
            response.status = 400
            render([errorCode: 400, errorMessage: "There is no downloaded web archive to deploy"] as JSON)
            return
        }
        command.localWebArchivePath = request.JSON.localWebArchivePath

        def source = command.localWebArchive
        def destination = new File(command.localWebArchivePath)
        def backup = new File(command.localWebArchive.absolutePath + ".backup")
        log.info "Copying web archive to backup " + source.absolutePath + " to " + backup.absolutePath
        backup.bytes = source.bytes

        log.info "Copying web archive to web container " + destination.absolutePath
        destination.bytes = source.bytes

        render([data: upgradeState(command)] as JSON)
    }

    private static Map upgradeState(UpgradeCommand command) {
        Integer remoteFileSize = command?.remoteFileSize
        Long localFileSize = command?.localWebArchive?.exists() ? command?.localWebArchive?.size() : null
        Integer progressPercentage = (remoteFileSize && remoteFileSize > 0 && localFileSize != null)
                ? Math.min(100, (int) (localFileSize * 100 / remoteFileSize))
                : 0
        [
                remoteWebArchiveUrl        : command?.remoteWebArchiveUrl,
                remoteFileSize             : remoteFileSize,
                localFileSize              : localFileSize,
                localWebArchivePath        : command?.localWebArchivePath,
                localWebArchiveAbsolutePath: command?.localWebArchive?.absolutePath,
                progressPercentage         : progressPercentage,
                downloadCancelled          : command?.future?.isCancelled() ?: false,
                downloadDone               : command?.future?.isDone() ?: false,
        ]
    }
}
