/**
* Copyright (c) 2012 Partners In Health.  All rights reserved.
* The use and distribution terms for this software are covered by the
* Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
* which can be found in the file epl-v10.html at the root of this distribution.
* By using this software in any fashion, you are agreeing to be bound by
* the terms of this license.
* You must not remove this notice, or any other, from this software.
**/
package org.pih.warehouse.data

import grails.converters.JSON
import org.springframework.http.HttpStatus

import org.pih.warehouse.core.Document
import org.pih.warehouse.core.DocumentCode

import java.nio.charset.Charset

class DataExportController {

    def dataService
    def index() {
        render(view: "/common/react")
    }

    /**
     * Runs the query stored in a data export document. The document must be a data export
     * (the contents of any other document are never executed) and the query must be a single
     * read-only statement.
     */
    def render() {
        Document document = Document.get(params.id)
        if (!document || document.documentType?.documentCode != DocumentCode.DATA_EXPORT) {
            response.status = HttpStatus.NOT_FOUND.value()
            render([errorCode   : HttpStatus.NOT_FOUND.value(),
                    errorMessage: g.message(code: 'dataExport.notFound.message')] as JSON)
            return
        }
        String query = document.fileContents ? new String(document.fileContents, Charset.defaultCharset()) : null
        if (query?.trim()) {
            List data
            try {
                data = dataService.executeReadOnlyQuery(query)
            } catch (IllegalArgumentException e) {
                log.error("Refusing to run data export ${document.id}: ${e.message}")
                response.status = HttpStatus.FORBIDDEN.value()
                render([errorCode   : HttpStatus.FORBIDDEN.value(),
                        errorMessage: g.message(code: 'dataExport.queryNotAllowed.message')] as JSON)
                return
            }
            if (params.format == "csv") {
                String csv = dataService.generateCsv(data)
                response.setHeader("Content-disposition", "attachment; filename=\"${document.name}.csv\"")
                render(contentType: "text/csv", text: csv.toString(), encoding: "UTF-8")
                return
            }
            render data as JSON
            return
        }
        render document as JSON

    }

}
