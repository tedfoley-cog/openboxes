package org.pih.warehouse.product

import grails.testing.gorm.DataTest
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.pih.warehouse.LocalizationUtil
import org.pih.warehouse.core.LocalizationService
import org.pih.warehouse.data.DataService
import org.pih.warehouse.product.Product
import org.pih.warehouse.product.ProductIdentifierService
import org.pih.warehouse.product.ProductService
import org.pih.warehouse.product.ProductType

@Unroll
class ProductServiceSpec extends Specification implements DataTest {

    @Shared
    ProductService service

    void setupSpec() {
        mockDomain Product
    }

    void setup() {
        service = new ProductService()
    }

    void 'getProducts returns the requested products'() {
        given:
        new Product(id: 1).save(validate: false)
        new Product(id: 2).save(validate: false)
        new Product(id: 3, active: false).save(validate: false)

        when:
        List<Product> products = service.getProducts(productIds as String[])

        then:
        products.size() == expectedNumProducts

        where:
        productIds      || expectedNumProducts
        null            || 0
        []              || 0
        ['2']           || 1
        ['3']           || 0
        ['1', '2', '3'] || 2
    }

    void 'searchProductDtos binds search terms as query parameters'() {
        given:
        GroovyMock(LocalizationUtil, global: true)
        LocalizationUtil.localizationService >> Stub(LocalizationService) {
            getCurrentLocale() >> Locale.ENGLISH
        }

        and: 'a data service that captures the query and its bound parameters'
        String capturedQuery = null
        Map capturedParams = null
        service.dataService = Stub(DataService) {
            executeQuery(_ as String, _ as Map) >> { String query, Map queryParams ->
                capturedQuery = query
                capturedParams = queryParams
                return []
            }
        }

        when:
        service.searchProductDtos(terms as String[])

        then: 'the terms are never interpolated into the query, so it contains no string literals at all'
        !capturedQuery.contains("'")

        and: 'every term is bound as a parameter'
        capturedParams.exactMatchTerm == expectedExactMatch
        capturedParams.locale == 'en'
        expectedBoundTerms.every { term -> capturedParams.values().contains(term) }

        where:
        terms                      || expectedExactMatch         | expectedBoundTerms
        null                       || ''                         | []
        []                         || ''                         | []
        ['aspirin']                || 'aspirin'                  | ['aspirin%', '%aspirin%']
        ['aspirin', '100mg']       || 'aspirin 100mg'            | ['100mg%', '%100mg%']
        ["x' union select 1 #"]    || "x' union select 1 #"      | ["x' union select 1 #%"]
        ['50%_off']                || '50%_off'                  | ['50\\%\\_off%', '%50\\%\\_off%']
        ['a\\b']                   || 'a\\b'                     | ['a\\\\b%', '%a\\\\b%']
    }

    @Ignore('The executeQuery in ProductService.validateProductIdentifier cannot be stubbed easily. It should be moved to a static method in the Domain class.')
    void 'saveProduct can create a product'() {
        given:
        ProductType productType = new ProductType()
        String productCode = 'testcode'

        and: 'the following mocks'
        // Product.metaClass.static.executeQuery = {String query, List params -> return [0]}
        service.productIdentifierService = Stub(ProductIdentifierService) {
            generate(_ as Product) >> productCode
        }

        when:
        def returnedProduct = service.saveProduct(new Product(id: 1, productType: productType))

        then:
        returnedProduct != null
        // Verify other fields such as productCode
    }
}
