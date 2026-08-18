package org.pih.warehouse.api

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

import org.pih.warehouse.core.Location
import org.pih.warehouse.order.Order
import org.pih.warehouse.order.OrderItem
import org.pih.warehouse.shipping.ShipmentService
import org.pih.warehouse.stockTransfer.StockTransferService

class StockTransferApiControllerAuthorizationSpec extends Specification
        implements ControllerUnitTest<StockTransferApiController>, DataTest {

    ShipmentService shipmentService
    StockTransferService stockTransferService

    Location currentLocation
    Location otherLocation
    Order foreignOrder
    OrderItem foreignOrderItem

    Class[] getDomainClassesToMock() {
        [Location, Order, OrderItem]
    }

    void setup() {
        shipmentService = Mock(ShipmentService)
        stockTransferService = Mock(StockTransferService)
        controller.shipmentService = shipmentService
        controller.stockTransferService = stockTransferService

        currentLocation = new Location(name: 'Current Depot').save(validate: false)
        otherLocation = new Location(name: 'Other Depot').save(validate: false)

        foreignOrder = new Order(origin: otherLocation, destination: otherLocation).save(validate: false)
        foreignOrderItem = new OrderItem(order: foreignOrder).save(validate: false)

        session.warehouse = [id: currentLocation.id]
    }

    void 'removeItem should respond 403 without deleting an item of an order at another location'() {
        given:
        params.id = foreignOrderItem.id

        when:
        controller.removeItem()

        then:
        0 * stockTransferService.deleteStockTransferItem(_)
        response.status == 403
    }

    void 'removeAllItems should respond 403 without deleting the items of an order at another location'() {
        given:
        params.id = foreignOrder.id

        when:
        controller.removeAllItems()

        then:
        0 * stockTransferService.deleteAllStockTransferItems(_)
        response.status == 403
    }

    void 'sendShipment should respond 403 without shipping an order at another location'() {
        given:
        params.id = foreignOrder.id

        when:
        controller.sendShipment()

        then:
        0 * shipmentService.sendShipment(_)
        response.status == 403
    }

    void 'rollback should respond 403 without rolling back an order at another location'() {
        given:
        params.id = foreignOrder.id

        when:
        controller.rollback()

        then:
        0 * stockTransferService.rollbackReturnOrder(_, _)
        response.status == 403
    }

    void 'sendShipment should ship an order that originates from the current location'() {
        given:
        Order order = new Order(origin: currentLocation, destination: otherLocation).save(validate: false)
        params.id = order.id

        when:
        controller.sendShipment()

        then:
        1 * shipmentService.sendShipment(order)
        response.status == 200
    }

    void 'rollback should roll back an order destined for the current location'() {
        given:
        Order order = new Order(origin: otherLocation, destination: currentLocation).save(validate: false)
        params.id = order.id

        when:
        controller.rollback()

        then:
        1 * stockTransferService.rollbackReturnOrder(order.id as String, _ as Location)
        response.status == 200
    }
}
