package org.pih.warehouse.api.client.core

import groovy.transform.InheritConstructors
import io.restassured.path.json.JsonPath
import org.grails.web.json.JSONObject
import org.springframework.boot.test.context.TestComponent

import org.pih.warehouse.api.client.base.ApiWrapper
import org.pih.warehouse.core.LocationType

@TestComponent
@InheritConstructors
class LocationTypeApiWrapper extends ApiWrapper<LocationTypeApi> {

    String createOK(LocationType locationType) {
        String body = new JSONObject()
                .put('name', locationType.name)
                .put('description', locationType.description)
                .put('locationTypeCode', locationType.locationTypeCode?.name())
                .put('sortOrder', locationType.sortOrder)
                .toString()
        return api.create(body, responseSpecUtil.CREATED_RESPONSE_SPEC)
                .jsonPath()
                .getString("data.id")
    }

    JsonPath updateOK(String locationTypeId, JSONObject body) {
        return api.update(locationTypeId, body.toString(), responseSpecUtil.OK_RESPONSE_SPEC).jsonPath()
    }

    JsonPath deleteOK(String locationTypeId) {
        return api.delete(locationTypeId, responseSpecUtil.NO_CONTENT_RESPONSE_SPEC).jsonPath()
    }
}
