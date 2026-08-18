package org.pih.warehouse.report

import grails.core.GrailsApplication
import spock.lang.Specification
import spock.lang.Unroll

import org.pih.warehouse.data.DataService

class ReportServiceForecastReportSpec extends Specification {

    ReportService reportService
    String capturedQuery
    Map capturedParams

    void setup() {
        reportService = new ReportService()
        reportService.grailsApplication = Stub(GrailsApplication) {
            getConfig() >> new ConfigSlurper().parse("openboxes { forecasting { enabled = true } }")
        }
        reportService.dataService = Stub(DataService) {
            executeQuery(_ as String, _ as Map) >> { String query, Map params ->
                capturedQuery = query
                capturedParams = params
                return []
            }
        }
    }

    @Unroll
    void "getForecastReport binds #paramName values instead of interpolating them into the query"() {
        given:
        Map params = [
                startDate    : new Date() - 30,
                endDate      : new Date(),
                originId     : "origin-1",
                (paramName)  : ["x') OR 1=1 -- ", "safe-value"] as String[],
        ]

        when:
        reportService.getForecastReport(params)

        then:
        !capturedQuery.contains("OR 1=1")
        !capturedQuery.contains("'")
        capturedQuery.contains("${column} in (:${bindPrefix}0,:${bindPrefix}1)")
        capturedParams["${bindPrefix}0"] == "x') OR 1=1 -- "
        capturedParams["${bindPrefix}1"] == "safe-value"

        where:
        paramName   | column                                          | bindPrefix
        "locations" | "pdd.destination_id"                            | "destinationId"
        "tags"      | "product_tag.tag_id"                            | "tagId"
        "catalogs"  | "product_catalog_item.product_catalog_id"       | "catalogId"
    }

    void "getForecastReport binds a single scalar filter value"() {
        given:
        Map params = [
                startDate: new Date() - 30,
                endDate  : new Date(),
                originId : "origin-1",
                tags     : "tag-1",
        ]

        when:
        reportService.getForecastReport(params)

        then:
        capturedQuery.contains("product_tag.tag_id in (:tagId0)")
        capturedParams.tagId0 == "tag-1"
        capturedParams.startDate == params.startDate
        capturedParams.endDate == params.endDate
        capturedParams.originId == "origin-1"
    }

    void "getForecastReport omits filters that are not provided"() {
        given:
        Map params = [
                startDate: new Date() - 30,
                endDate  : new Date(),
                originId : "origin-1",
                locations: "null",
        ]

        when:
        reportService.getForecastReport(params)

        then:
        !capturedQuery.contains("destination_id in")
        !capturedQuery.contains("product_tag")
        !capturedQuery.contains("product_catalog_item")
        capturedParams.keySet() == ["startDate", "endDate", "originId"] as Set
    }
}
