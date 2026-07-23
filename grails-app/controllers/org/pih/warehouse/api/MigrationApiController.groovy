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
import org.hibernate.criterion.CriteriaSpecification
import org.pih.warehouse.core.Location
import org.pih.warehouse.product.ProductAvailability
import org.springframework.http.HttpStatus

/**
 * Read-and-refresh API over the data-migration materialized views backing
 * the React migration/materializedViews and migration/productAvailability
 * screens.
 */
class MigrationApiController {

    def dataService
    def locationService
    def productAvailabilityService
    def reportService

    def materializedViews() {
        def productDemandCount = dataService.executeQuery("select count(*) as count from product_demand_details")[0]?.count ?: 0
        def productAvailabilityCount = dataService.executeQuery("select count(*) as count from product_availability")[0]?.count ?: 0
        render([data: [
                productDemandCount      : productDemandCount,
                productAvailabilityCount: productAvailabilityCount,
        ]] as JSON)
    }

    def productAvailability() {
        def countByLocation = ProductAvailability.createCriteria().list {
            resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)
            projections {
                count("id", "count")
                groupProperty("location", "location")
            }
        }
        def data = locationService.depots.collect { Location location ->
            def count = countByLocation.find { it.location == location }?.count ?: null
            [
                    locationId              : location.id,
                    locationName            : location.name,
                    productAvailabilityCount: count,
            ]
        }.sort { it.productAvailabilityCount }
        render([data: data] as JSON)
    }

    def productAvailabilityCount() {
        Location location = Location.get(params.locationId)
        if (!location) {
            response.status = HttpStatus.NOT_FOUND.value()
            render([errorCode: HttpStatus.NOT_FOUND.value(),
                    errorMessage: "No location found for id ${params.locationId}".toString()] as JSON)
            return
        }
        def results = ProductAvailability.createCriteria().list {
            resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)
            projections {
                count("id", "count")
            }
            eq("location", location)
        }
        def count = results ? results[0].count : null
        render([data: [locationId: location.id, count: count]] as JSON)
    }

    def calculateProductAvailability() {
        Location location = Location.get(params.locationId)
        if (!location) {
            response.status = HttpStatus.NOT_FOUND.value()
            render([errorCode: HttpStatus.NOT_FOUND.value(),
                    errorMessage: "No location found for id ${params.locationId}".toString()] as JSON)
            return
        }
        def binLocations = productAvailabilityService.calculateBinLocations(location)
        render([data: [locationId: location.id, count: binLocations.size()]] as JSON)
    }

    def refreshProductAvailability() {
        String locationId = params.locationId ?: request.JSON?.locationId
        if (locationId) {
            Location location = Location.get(locationId)
            if (!location) {
                response.status = HttpStatus.NOT_FOUND.value()
                render([errorCode: HttpStatus.NOT_FOUND.value(),
                        errorMessage: "No location found for id ${locationId}".toString()] as JSON)
                return
            }
            productAvailabilityService.refreshProductAvailability(location, true)
            render([data: "Refreshed product availability for location ${location.name}".toString()] as JSON)
            return
        }
        productAvailabilityService.refreshProductAvailability(Boolean.TRUE)
        render([data: "Refreshed product availability"] as JSON)
    }

    def refreshProductDemand() {
        reportService.refreshProductDemandData()
        render([data: "Refreshed product demand data"] as JSON)
    }
}
