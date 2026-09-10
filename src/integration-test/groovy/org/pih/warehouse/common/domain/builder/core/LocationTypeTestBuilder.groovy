package org.pih.warehouse.common.domain.builder.core

import groovy.transform.InheritConstructors

import org.pih.warehouse.common.domain.builder.base.TestBuilder
import org.pih.warehouse.core.LocationType
import org.pih.warehouse.core.LocationTypeCode

@InheritConstructors
class LocationTypeTestBuilder extends TestBuilder<LocationType> {

    @Override
    protected Map<String, Object> getDefaults() {
        return [
                name           : randomUtil.randomStringFieldValue("Location Type"),
                locationTypeCode: LocationTypeCode.DEPOT,
                sortOrder      : 0,
        ] as Map<String, Object>
    }

    LocationTypeTestBuilder name(String name) {
        args.name = name
        return this
    }

    LocationTypeTestBuilder locationTypeCode(LocationTypeCode locationTypeCode) {
        args.locationTypeCode = locationTypeCode
        return this
    }
}
