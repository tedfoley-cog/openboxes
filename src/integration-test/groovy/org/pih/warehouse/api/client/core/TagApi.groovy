package org.pih.warehouse.api.client.core

import groovy.transform.InheritConstructors
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.Method
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import io.restassured.specification.ResponseSpecification
import org.springframework.boot.test.context.TestComponent

import org.pih.warehouse.api.client.base.AuthenticatedApi

@TestComponent
@InheritConstructors
class TagApi extends AuthenticatedApi {

    Response list(ResponseSpecification responseSpec) {
        return request(null, responseSpec, Method.GET, "/tags")
    }

    Response list(String q, ResponseSpecification responseSpec) {
        RequestSpecification requestSpec = new RequestSpecBuilder()
                .addQueryParam("q", q)
                .build()
        return request(requestSpec, responseSpec, Method.GET, "/tags")
    }

    Response read(String id, ResponseSpecification responseSpec) {
        RequestSpecification requestSpec = new RequestSpecBuilder()
                .addPathParam("id", id)
                .build()
        return request(requestSpec, responseSpec, Method.GET, "/tags/{id}")
    }

    Response create(String body, ResponseSpecification responseSpec) {
        RequestSpecification requestSpec = new RequestSpecBuilder()
                .setBody(body)
                .build()
        return request(requestSpec, responseSpec, Method.POST, "/tags")
    }

    Response update(String id, String body, ResponseSpecification responseSpec) {
        RequestSpecification requestSpec = new RequestSpecBuilder()
                .addPathParam("id", id)
                .setBody(body)
                .build()
        return request(requestSpec, responseSpec, Method.PUT, "/tags/{id}")
    }

    Response delete(String id, ResponseSpecification responseSpec) {
        RequestSpecification requestSpec = new RequestSpecBuilder()
                .addPathParam("id", id)
                .build()
        return request(requestSpec, responseSpec, Method.DELETE, "/tags/{id}")
    }

    Response addProducts(String id, String body, ResponseSpecification responseSpec) {
        RequestSpecification requestSpec = new RequestSpecBuilder()
                .addPathParam("id", id)
                .setBody(body)
                .build()
        return request(requestSpec, responseSpec, Method.POST, "/tags/{id}/products")
    }

    Response removeProduct(String id, String productId, ResponseSpecification responseSpec) {
        RequestSpecification requestSpec = new RequestSpecBuilder()
                .addPathParam("id", id)
                .addPathParam("productId", productId)
                .build()
        return request(requestSpec, responseSpec, Method.DELETE, "/tags/{id}/products/{productId}")
    }
}
