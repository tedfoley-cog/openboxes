package org.pih.warehouse.api.spec.core

import io.restassured.builder.ResponseSpecBuilder
import org.apache.http.HttpStatus
import org.grails.web.json.JSONObject
import org.hamcrest.Matchers
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

import org.pih.warehouse.api.client.core.TagApiWrapper
import org.pih.warehouse.api.spec.base.ApiSpec
import org.pih.warehouse.common.domain.builder.core.TagTestBuilder
import org.pih.warehouse.core.Tag

class TagApiSpec extends ApiSpec {

    @Autowired
    TagApiWrapper tagApiWrapper

    @Shared
    Tag tag

    @Override
    void setupData() {
        tag = new TagTestBuilder().build(true)
    }

    @Override
    void cleanupData() {
        if (tag?.id) {
            Tag existingTag = Tag.get(tag.id)
            if (existingTag) {
                existingTag.delete(flush: true)
            }
        }
    }

    void 'list tags should return all tags including the one created in setup'() {
        expect:
        tagApiWrapper.api.list(new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_OK)
                .expectBody('data.id', Matchers.hasItem(tag.id))
                .build())
    }

    void 'list tags with q filter matching tag should include matching tag'() {
        expect:
        tagApiWrapper.api.list(tag.tag, new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_OK)
                .expectBody('data.id', Matchers.hasItem(tag.id))
                .build())
    }

    void 'list tags with q filter not matching should not include tag'() {
        expect:
        tagApiWrapper.api.list("ZZZZ_NO_MATCH", new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_OK)
                .expectBody('data.id', Matchers.not(Matchers.hasItem(tag.id)))
                .build())
    }

    void 'read tag by id should return tag data when tag exists'() {
        expect:
        tagApiWrapper.api.read(tag.id, new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_OK)
                .expectBody('data.tag', Matchers.equalTo(tag.tag))
                .expectBody('data.isActive', Matchers.equalTo(tag.isActive))
                .expectBody('data.products', Matchers.empty())
                .expectBody('data.version', Matchers.notNullValue())
                .build())
    }

    void 'read tag by id should fail when tag does not exist'() {
        expect:
        tagApiWrapper.api.read(INVALID_ID, responseSpecUtil.NOT_FOUND_RESPONSE_SPEC)
    }

    void 'create tag should succeed and return new id when tag is provided'() {
        given:
        Tag newTag = new TagTestBuilder().build()

        when:
        String tagId = tagApiWrapper.createOK(newTag)

        then:
        tagId != null

        cleanup:
        tagApiWrapper.deleteOK(tagId)
    }

    void 'create tag should return 400 when tag is blank or missing'() {
        expect:
        tagApiWrapper.api.create(new JSONObject().put('tag', '').toString(),
                responseSpecUtil.buildStatusCodeResponseSpec(HttpStatus.SC_BAD_REQUEST))
        tagApiWrapper.api.create(new JSONObject().toString(),
                responseSpecUtil.buildStatusCodeResponseSpec(HttpStatus.SC_BAD_REQUEST))
    }

    void 'update tag should return updated tag data when valid data is provided'() {
        given:
        String updatedTag = "Updated ${tag.tag}"
        JSONObject body = new JSONObject()
                .put('tag', updatedTag)
                .put('isActive', false)

        when:
        def response = tagApiWrapper.updateOK(tag.id, body)

        then:
        response.getString('data.id') == tag.id
        response.getString('data.tag') == updatedTag
        response.getBoolean('data.isActive') == false
    }

    void 'update tag should fail when tag does not exist'() {
        given:
        JSONObject body = new JSONObject()
                .put('tag', 'Some Tag')
                .put('isActive', true)

        expect:
        tagApiWrapper.api.update(INVALID_ID, body.toString(), responseSpecUtil.NOT_FOUND_RESPONSE_SPEC)
    }

    void 'add products to tag should return the product in tag data'() {
        given:
        String tagId = tagApiWrapper.createOK(new TagTestBuilder().build())

        expect:
        tagApiWrapper.api.addProducts(tagId,
                new JSONObject().put('productCodes', product.productCode).toString(),
                new ResponseSpecBuilder()
                        .expectStatusCode(HttpStatus.SC_OK)
                        .expectBody('data.products.id', Matchers.hasItem(product.id))
                        .build())

        cleanup:
        tagApiWrapper.deleteOK(tagId)
    }

    void 'add products to tag should return 400 when product codes are empty'() {
        given:
        String tagId = tagApiWrapper.createOK(new TagTestBuilder().build())

        expect:
        tagApiWrapper.api.addProducts(tagId,
                new JSONObject().put('productCodes', '').toString(),
                responseSpecUtil.buildStatusCodeResponseSpec(HttpStatus.SC_BAD_REQUEST))

        cleanup:
        tagApiWrapper.deleteOK(tagId)
    }

    void 'remove product from tag should remove the product from tag data'() {
        given:
        String tagId = tagApiWrapper.createOK(new TagTestBuilder().build())
        tagApiWrapper.api.addProducts(tagId,
                new JSONObject().put('productCodes', product.productCode).toString(),
                responseSpecUtil.OK_RESPONSE_SPEC)

        expect:
        tagApiWrapper.api.removeProduct(tagId, product.id, new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_OK)
                .expectBody('data.products.id', Matchers.not(Matchers.hasItem(product.id)))
                .build())

        cleanup:
        tagApiWrapper.deleteOK(tagId)
    }

    void 'delete tag should succeed when tag exists'() {
        given:
        String tagId = tagApiWrapper.createOK(new TagTestBuilder().build())

        expect:
        tagApiWrapper.deleteOK(tagId)
    }

    void 'delete tag should fail when tag does not exist'() {
        expect:
        tagApiWrapper.api.delete(INVALID_ID, responseSpecUtil.NOT_FOUND_RESPONSE_SPEC)
    }
}
