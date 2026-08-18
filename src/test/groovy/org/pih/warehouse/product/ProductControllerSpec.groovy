package org.pih.warehouse.product

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

import org.pih.warehouse.core.Document

class ProductControllerSpec extends Specification implements ControllerUnitTest<ProductController>, DataTest {

    Class[] getDomainClassesToMock() {
        [Document]
    }

    private static Document buildDocument(Map properties = [:]) {
        return new Document([
                id          : "document-1",
                filename    : "product-image.png",
                contentType : "image/png",
                fileContents: "file-contents".bytes,
        ] + properties)
    }

    private void stubProductDocument(Document document) {
        controller.productService = [getProductDocument: { String documentId -> document }]
    }

    void 'renderImage does not serve a document that is not attached to a product'() {
        given:
        stubProductDocument(null)

        when:
        params.id = "shipment-document-id"
        controller.renderImage()

        then:
        response.status == 404
        response.contentAsByteArray.length == 0
    }

    void 'renderImage serves an image attached to a product'() {
        given:
        stubProductDocument(buildDocument())

        when:
        params.id = "document-1"
        controller.renderImage()

        then:
        response.status == 200
        response.contentType.startsWith("image/png")
        response.getHeader("X-Content-Type-Options") == "nosniff"
        new String(response.contentAsByteArray) == "file-contents"
    }

    void 'renderImage does not serve a document whose content type is not a renderable image'() {
        given:
        stubProductDocument(buildDocument(contentType: "image/svg+xml", filename: "product-image.svg"))

        when:
        params.id = "document-1"
        controller.renderImage()

        then:
        response.status == 404
        response.contentAsByteArray.length == 0
    }

    void 'downloadDocument does not serve a document that is not attached to a product'() {
        given:
        stubProductDocument(null)

        when:
        params.id = "invoice-document-id"
        controller.downloadDocument()

        then:
        response.status == 404
        response.contentAsByteArray.length == 0
    }

    void 'downloadDocument serves a document attached to a product as an opaque attachment'() {
        given:
        stubProductDocument(buildDocument(contentType: "application/pdf", filename: "specification.pdf"))

        when:
        params.id = "document-1"
        controller.downloadDocument()

        then:
        response.status == 200
        response.contentType.startsWith("application/octet-stream")
        response.getHeader("Content-disposition") == 'attachment;filename="specification.pdf"'
        new String(response.contentAsByteArray) == "file-contents"
    }

    void 'downloadDocument strips characters that could break out of the content disposition header'() {
        given:
        stubProductDocument(buildDocument(filename: "../evil\"\r\nX-Injected: yes.pdf"))

        when:
        params.id = "document-1"
        controller.downloadDocument()

        then:
        response.getHeader("Content-disposition") == 'attachment;filename="evil___X-Injected_ yes.pdf"'
        response.getHeader("X-Injected") == null
    }

    void 'viewThumbnail does not serve a document that is not attached to a product'() {
        given:
        stubProductDocument(null)

        when:
        params.id = "order-document-id"
        controller.viewThumbnail()

        then:
        response.status == 404
        response.contentAsByteArray.length == 0
    }
}
