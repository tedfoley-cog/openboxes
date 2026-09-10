package org.pih.warehouse.api.spec.core

import io.restassured.builder.ResponseSpecBuilder
import org.apache.http.HttpStatus
import org.grails.web.json.JSONObject
import org.hamcrest.Matchers
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

import org.pih.warehouse.api.client.core.LocationTypeApiWrapper
import org.pih.warehouse.api.spec.base.ApiSpec
import org.pih.warehouse.common.domain.builder.core.LocationTypeTestBuilder
import org.pih.warehouse.core.LocationType

class LocationTypeApiSpec extends ApiSpec {

    @Autowired
    LocationTypeApiWrapper locationTypeApiWrapper

    @Shared
    LocationType locationType

    @Override
    void setupData() {
        locationType = new LocationTypeTestBuilder().build(true)
    }

    @Override
    void cleanupData() {
        if (locationType?.id) {
            LocationType existingLocationType = LocationType.get(locationType.id)
            if (existingLocationType) {
                existingLocationType.delete(flush: true)
            }
        }
    }

    void 'list location types should return all location types including the one created in setup'() {
        expect:
        locationTypeApiWrapper.api.list(new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_OK)
                .expectBody('data.id', Matchers.hasItem(locationType.id))
                .build())
    }

    void 'list location types with q prefix filter should include matching location type'() {
        expect:
        locationTypeApiWrapper.api.list(locationType.name.substring(0, Math.min(10, locationType.name.size())),
                new ResponseSpecBuilder()
                        .expectStatusCode(HttpStatus.SC_OK)
                        .expectBody('data.id', Matchers.hasItem(locationType.id))
                        .build())
    }

    void 'list location types with q filter not matching should not include location type'() {
        expect:
        locationTypeApiWrapper.api.list("ZZZZ_NO_MATCH", new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_OK)
                .expectBody('data.id', Matchers.not(Matchers.hasItem(locationType.id)))
                .build())
    }

    void 'read location type by id should return location type data when it exists'() {
        expect:
        locationTypeApiWrapper.api.read(locationType.id, new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_OK)
                .expectBody('data.name', Matchers.equalTo(locationType.name))
                .expectBody('data.locationTypeCode', Matchers.equalTo('DEPOT'))
                .expectBody('data.supportedActivities', Matchers.empty())
                .expectBody('data.sortOrder', Matchers.equalTo(0))
                .expectBody('data.version', Matchers.notNullValue())
                .build())
    }

    void 'read location type by id should fail when location type does not exist'() {
        expect:
        locationTypeApiWrapper.api.read(INVALID_ID, responseSpecUtil.NOT_FOUND_RESPONSE_SPEC)
    }

    void 'create location type should succeed and return new id when fields are valid'() {
        given:
        LocationType newLocationType = new LocationTypeTestBuilder().build()

        when:
        String locationTypeId = locationTypeApiWrapper.createOK(newLocationType)

        then:
        locationTypeId != null

        cleanup:
        locationTypeApiWrapper.deleteOK(locationTypeId)
    }

    void 'create location type should return 400 when name exceeds max length of 255 characters'() {
        given:
        String body = new JSONObject()
                .put('name', 'a' * 256)
                .put('locationTypeCode', 'DEPOT')
                .put('sortOrder', 0)
                .toString()

        expect:
        locationTypeApiWrapper.api.create(body,
                responseSpecUtil.buildStatusCodeResponseSpec(HttpStatus.SC_BAD_REQUEST))
    }

    void 'create location type should return 400 when NONE is combined with other supported activities'() {
        given:
        String body = new JSONObject()
                .put('name', "Invalid ${UUID.randomUUID()}")
                .put('locationTypeCode', 'DEPOT')
                .put('supportedActivities', ['NONE', 'MANAGE_INVENTORY'])
                .toString()

        expect:
        locationTypeApiWrapper.api.create(body,
                responseSpecUtil.buildStatusCodeResponseSpec(HttpStatus.SC_BAD_REQUEST))
    }

    void 'update location type should return updated data when valid data is provided'() {
        given:
        String updatedName = "Updated ${locationType.name}"
        JSONObject body = new JSONObject()
                .put('name', updatedName)
                .put('description', 'Updated description')
                .put('sortOrder', 10)

        when:
        def response = locationTypeApiWrapper.updateOK(locationType.id, body)

        then:
        response.getString('data.id') == locationType.id
        response.getString('data.name') == updatedName
        response.getString('data.description') == 'Updated description'
        response.getInt('data.sortOrder') == 10
    }

    void 'update location type should fail when location type does not exist'() {
        given:
        JSONObject body = new JSONObject()
                .put('name', 'Some Name')
                .put('locationTypeCode', 'DEPOT')

        expect:
        locationTypeApiWrapper.api.update(INVALID_ID, body.toString(), responseSpecUtil.NOT_FOUND_RESPONSE_SPEC)
    }

    void 'delete location type should succeed when location type exists'() {
        given:
        String locationTypeId = locationTypeApiWrapper.createOK(new LocationTypeTestBuilder().build())

        expect:
        locationTypeApiWrapper.deleteOK(locationTypeId)
    }

    void 'delete location type should fail when location type does not exist'() {
        expect:
        locationTypeApiWrapper.api.delete(INVALID_ID, responseSpecUtil.NOT_FOUND_RESPONSE_SPEC)
    }
}
