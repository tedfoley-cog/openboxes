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
import org.springframework.http.HttpStatus

import org.pih.warehouse.shipping.Shipment
import org.pih.warehouse.shipping.ShipmentItem

class ShipmentApiController {

    def messageSource

    private static Map addressToJson(address) {
        address ? [
                address        : address.address,
                address2       : address.address2,
                city           : address.city,
                stateOrProvince: address.stateOrProvince,
                postalCode     : address.postalCode,
                country        : address.country,
        ] : null
    }

    private static Map locationToJson(location) {
        location ? [
                id     : location.id,
                name   : location.name,
                address: addressToJson(location.address),
        ] : null
    }

    /**
     * Data for the migrated outbound return delivery note print screen
     * (mirrors the legacy deliveryNote/printOutboundReturn.gsp view model).
     */
    def outboundReturnPrintData() {
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            response.status = HttpStatus.NOT_FOUND.value()
            render([errorCode: HttpStatus.NOT_FOUND.value(), errorMessage: "Shipment ${params.id} not found"] as JSON)
            return
        }
        def sortedItems = shipment.shipmentItems?.sort { it.product?.name } ?: []
        def sortedReceipts = shipment.receipts?.sort { it.dateCreated } ?: []
        render([data: [
                id                   : shipment.id,
                shipmentNumber       : shipment.shipmentNumber,
                name                 : shipment.name,
                origin               : locationToJson(shipment.origin),
                destination          : locationToJson(shipment.destination),
                expectedShippingDate : shipment.expectedShippingDate,
                receivedDate         : sortedReceipts ? sortedReceipts.last()?.actualDeliveryDate : null,
                referenceNumber      : shipment.referenceNumbers ? shipment.referenceNumbers.first()?.identifier : null,
                driverName           : shipment.driverName,
                additionalInformation: shipment.additionalInformation,
                shipmentItems        : sortedItems.collect { ShipmentItem item ->
                    def receiptItems = shipment.receipts?.collectMany { r ->
                        r.receiptItems?.findAll { ri -> ri.shipmentItem?.id == item.id && ri.quantityReceived > 0 } ?: []
                    } ?: []
                    [
                            id            : item.id,
                            productId     : item.product?.id,
                            productCode   : item.product?.productCode,
                            productName   : item.product?.name,
                            lotNumber     : item.lotNumber,
                            expirationDate: item.expirationDate,
                            quantity      : item.quantity,
                            receiptItems  : receiptItems.collect { ri ->
                                [
                                        id              : ri.id,
                                        productId       : ri.product?.id,
                                        productCode     : ri.product?.productCode,
                                        productName     : ri.product?.name,
                                        lotNumber       : ri.lotNumber,
                                        expirationDate  : ri.expirationDate,
                                        quantityReceived: ri.quantityReceived,
                                        comment         : ri.comment,
                                ]
                            },
                    ]
                },
        ]] as JSON)
    }

    /**
     * Data for the migrated goods receipt note print screen (mirrors the
     * legacy goodsReceiptNote/print.gsp view model). Receipt items are
     * ordered with split items first, like the legacy _body.gsp sort.
     */
    def goodsReceiptNotePrintData() {
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            response.status = HttpStatus.NOT_FOUND.value()
            render([errorCode: HttpStatus.NOT_FOUND.value(), errorMessage: "Shipment ${params.id} not found"] as JSON)
            return
        }
        String statusCode = shipment.status?.code?.name()
        String statusName = statusCode ? messageSource.getMessage("enum.ShipmentStatusCode.${statusCode}", null, statusCode, request?.locale) : null
        def receipts = shipment.receipts?.sort { it.dateCreated } ?: []
        def shipmentItems = shipment.sortShipmentItemsBySortOrder()?.findAll { it.receiptItems } ?: []
        render([data: [
                id                : shipment.id,
                shipmentNumber    : shipment.shipmentNumber,
                name              : shipment.name,
                status            : statusName,
                origin            : locationToJson(shipment.origin),
                destination       : locationToJson(shipment.destination),
                actualShippingDate: shipment.actualShippingDate,
                lastReceiptDate   : receipts ? receipts.last()?.actualDeliveryDate : null,
                receipts          : receipts.collect { [id: it.id, receiptNumber: it.receiptNumber] },
                shipmentItems     : shipmentItems.collect { ShipmentItem item ->
                    def receiptItems = item.receiptItems.sort { !it.isSplitItem }
                    [
                            id            : item.id,
                            productCode   : item.product?.productCode,
                            productName   : item.product?.displayNameOrDefaultName,
                            lotNumber     : item.inventoryItem?.lotNumber,
                            expirationDate: item.inventoryItem?.expirationDate,
                            unitOfMeasure : item.inventoryItem?.product?.unitOfMeasure,
                            quantityShipped: item.quantity,
                            hasSplit      : receiptItems.any { it.isSplitItem },
                            receiptItems  : receiptItems.collect { ri ->
                                [
                                        id              : ri.id,
                                        receiptId       : ri.receipt?.id,
                                        lotNumber       : ri.inventoryItem?.lotNumber,
                                        expirationDate  : ri.inventoryItem?.expirationDate,
                                        quantityShipped : ri.quantityShipped,
                                        quantityReceived: ri.quantityReceived,
                                        comment         : ri.comment,
                                        isSplitItem     : ri.isSplitItem,
                                ]
                            },
                    ]
                },
        ]] as JSON)
    }
}
