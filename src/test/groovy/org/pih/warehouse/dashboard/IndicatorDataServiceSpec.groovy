package org.pih.warehouse.dashboard

import org.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

class IndicatorDataServiceSpec extends Specification {

    private static GrailsParameterMap buildParams(Map parameters) {
        return new GrailsParameterMap(parameters, new MockHttpServletRequest())
    }

    void 'buildCategoryFilter returns no filter when the category filter is not selected'() {
        when:
        Map categoryFilter = IndicatorDataService.buildCategoryFilter(buildParams(parameters))

        then:
        categoryFilter.extraCondition == ''
        categoryFilter.conditionStarter == 'where'
        categoryFilter.queryParams == [:]

        where:
        parameters << [
                [:],
                [value: ['1', '2']],
                [listFiltersSelected: 'category'],
                [listFiltersSelected: 'destination', value: ['1']],
        ]
    }

    void 'buildCategoryFilter binds every selected category as a named parameter'() {
        when:
        Map categoryFilter = IndicatorDataService.buildCategoryFilter(
                buildParams([listFiltersSelected: 'category', value: ['1', '2']]))

        then:
        categoryFilter.extraCondition.contains('c.id in (:categoryId0, :categoryId1)')
        categoryFilter.conditionStarter == 'and'
        categoryFilter.queryParams == [categoryId0: '1', categoryId1: '2']
    }

    void 'buildCategoryFilter does not interpolate category values into the query'() {
        given:
        String payload = "1' UNION SELECT password_hash, 1 FROM user -- "

        when:
        Map categoryFilter = IndicatorDataService.buildCategoryFilter(
                buildParams([listFiltersSelected: 'category', value: payload]))

        then:
        !categoryFilter.extraCondition.contains(payload)
        !categoryFilter.extraCondition.contains("'")
        categoryFilter.extraCondition.contains('c.id in (:categoryId0)')
        categoryFilter.queryParams == [categoryId0: payload]
    }
}
