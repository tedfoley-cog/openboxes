package org.pih.warehouse.api.client.core

import groovy.transform.InheritConstructors
import io.restassured.path.json.JsonPath
import org.grails.web.json.JSONObject
import org.springframework.boot.test.context.TestComponent

import org.pih.warehouse.api.client.base.ApiWrapper
import org.pih.warehouse.core.Tag

@TestComponent
@InheritConstructors
class TagApiWrapper extends ApiWrapper<TagApi> {

    String createOK(Tag tag) {
        String body = new JSONObject()
                .put('tag', tag.tag)
                .put('isActive', tag.isActive)
                .toString()
        return api.create(body, responseSpecUtil.CREATED_RESPONSE_SPEC)
                .jsonPath()
                .getString("data.id")
    }

    JsonPath updateOK(String tagId, JSONObject body) {
        return api.update(tagId, body.toString(), responseSpecUtil.OK_RESPONSE_SPEC).jsonPath()
    }

    JsonPath deleteOK(String tagId) {
        return api.delete(tagId, responseSpecUtil.NO_CONTENT_RESPONSE_SPEC).jsonPath()
    }
}
