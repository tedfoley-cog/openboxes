package org.pih.warehouse.data

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

import org.pih.warehouse.core.Document
import org.pih.warehouse.core.DocumentCode
import org.pih.warehouse.core.DocumentType

/**
 * The data export renderer executes the contents of a stored document as SQL, so it must
 * only ever run documents filed under a DATA_EXPORT document type.
 */
class DataExportControllerSpec extends Specification implements ControllerUnitTest<DataExportController>, DataTest {

    void setupSpec() {
        mockDomains(Document, DocumentType)
    }

    private Document createDocument(DocumentCode documentCode, String contents) {
        DocumentType documentType = documentCode ? new DocumentType(
            name: documentCode.name(),
            documentCode: documentCode,
        ).save(failOnError: true, validate: false) : null

        return new Document(
            name: "document-${documentCode}",
            filename: "document.txt",
            fileContents: contents.bytes,
            contentType: "text/plain",
            documentType: documentType,
        ).save(failOnError: true, validate: false)
    }

    void "render should not execute a document that is not a data export"() {
        given:
        Document document = createDocument(DocumentCode.PRODUCT_MANUAL, "select * from user")
        controller.params.id = document.id
        controller.dataService = Mock(DataService)

        when:
        controller.render()

        then:
        0 * controller.dataService.executeReadOnlyQuery(_)
        response.status == 404
    }

    void "render should not execute an unknown document id"() {
        given:
        controller.params.id = "no-such-document"
        controller.dataService = Mock(DataService)

        when:
        controller.render()

        then:
        0 * controller.dataService.executeReadOnlyQuery(_)
        response.status == 404
    }

    void "render should execute a data export document once, in read-only mode"() {
        given:
        Document document = createDocument(DocumentCode.DATA_EXPORT, "select id from product")
        controller.params.id = document.id
        controller.dataService = Mock(DataService)

        when:
        controller.render()

        then:
        1 * controller.dataService.executeReadOnlyQuery("select id from product") >> [[id: "1"]]
        0 * controller.dataService.executeQuery(_)
        response.status == 200
    }

    void "render should return a bad request when the stored query is rejected"() {
        given:
        Document document = createDocument(DocumentCode.DATA_EXPORT, "delete from product")
        controller.params.id = document.id
        controller.dataService = Mock(DataService)

        when:
        controller.render()

        then:
        1 * controller.dataService.executeReadOnlyQuery(_) >> { throw new IllegalArgumentException("Query must be a read-only SELECT statement") }
        response.status == 400
    }
}
