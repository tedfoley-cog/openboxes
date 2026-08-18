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
import org.pih.warehouse.core.Document
import org.pih.warehouse.core.DocumentCode
import org.pih.warehouse.core.DocumentType

import java.nio.charset.Charset

class DataExportController {

    def dataService
    def index() {
        render(view: "/common/react")
    }

    def render() {
        Document document = Document.get(params.id)
        if (!document || !isDataExportDocument(document)) {
            response.sendError(404, "Data export not found")
            return
        }

        String query = document.fileContents ? new String(document.fileContents, Charset.defaultCharset()) : null
        if (!query?.trim()) {
            render document as JSON
            return
        }

        List data
        try {
            data = dataService.executeReadOnlyQuery(query)
        } catch (IllegalArgumentException e) {
            log.error("Refusing to run data export ${document.id}: ${e.message}")
            response.sendError(400, e.message)
            return
        }

        if (params.format == "csv") {
            String csv = dataService.generateCsv(data)
            response.setHeader("Content-disposition", "attachment; filename=\"${document.name}.csv\"")
            render(contentType: "text/csv", text: csv.toString(), encoding: "UTF-8")
            return
        }
        render data as JSON
    }

    /**
     * A document is only executable as a data export if it was filed under a document type
     * with the DATA_EXPORT document code. Documents uploaded through the generic document
     * endpoints (product manuals, shipping documents, etc.) must never be executed.
     */
    private static boolean isDataExportDocument(Document document) {
        DocumentType documentType = document.documentType
        return documentType?.documentCode == DocumentCode.DATA_EXPORT
    }

}
