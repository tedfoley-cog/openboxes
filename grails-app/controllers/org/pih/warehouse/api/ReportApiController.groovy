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
import org.apache.commons.lang.StringEscapeUtils
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Location
import org.pih.warehouse.product.Product
import org.pih.warehouse.report.ChecklistReportCommand
import org.pih.warehouse.shipping.Shipment

class ReportApiController {

    def userService
    def inventoryService
    def reportService
    def productAvailabilityService

    /**
     * Rows backing the React report/showBinLocationReport screen. Mirrors
     * JsonController.getBinLocationReport (the dataTables source the legacy
     * GSP consumed), including finance-role gating of unit cost / total value.
     */
    def binLocationReport() {
        String locationId = params?.location?.id ?: session?.warehouse?.id
        Location location = Location.get(locationId)
        if (!location) {
            response.status = 404
            render([errorMessage: "Location with id ${locationId} not found"] as JSON)
            return
        }
        def data = productAvailabilityService.getQuantityOnHandByBinLocation(location)

        if (params.status) {
            data = data.findAll { it.status == params.status }
        }

        def hasRoleFinance = userService.hasRoleFinance(session?.user)

        data = data.collect {
            def quantity = it?.quantity ?: 0
            def quantityAvailableToPromise = it?.quantityAvailableToPromise ?: 0
            def unitCost = hasRoleFinance ? (it?.product?.pricePerUnit ?: 0.0) : null
            def totalValue = hasRoleFinance ? g.formatNumber(number: quantity * unitCost) : null
            [
                    id                        : it.product?.id,
                    status                    : g.message(code: "binLocationSummary.${it.status}.label"),
                    productCode               : it.product?.productCode,
                    productName               : it?.product?.name,
                    displayName               : it?.product?.displayName,
                    productGroup              : it?.product?.genericProduct?.name,
                    category                  : it?.product?.category?.name,
                    lotNumber                 : it?.inventoryItem?.lotNumber,
                    lotStatus                 : it?.inventoryItem?.lotStatus?.toString(),
                    expirationDate            : g.formatDate(date: it?.inventoryItem?.expirationDate, format: "dd/MMM/yyyy"),
                    unitOfMeasure             : it?.product?.unitOfMeasure,
                    zone                      : it?.binLocation?.zone?.name ?: "",
                    binLocation               : it?.binLocation?.name ?: "Default",
                    isOnHold                  : it?.binLocation?.isOnHold(),
                    quantity                  : quantity,
                    quantityAvailableToPromise: quantityAvailableToPromise,
                    unitCost                  : unitCost,
                    totalValue                : totalValue,
                    handlingIcons             : it.product?.getHandlingIcons()
            ]
        }
        render([data: data, location: [id: location.id, name: location.name]] as JSON)
    }

    /**
     * Rows backing the React report/showCycleCountReport screen. Mirrors the
     * model computed by ReportController.showCycleCountReport (non-print
     * branch), preserving the legacy CSV-escaped values.
     */
    def cycleCountReport() {
        Location location = Location.load(session.warehouse.id)
        List binLocations = inventoryService.getQuantityByBinLocation(location)

        List rows = binLocations.collect { row ->
            Product product = Product.get(row?.product?.id)
            def latestInventoryDate = row?.product?.latestInventoryDate(location.id) ?: row?.product.earliestReceivingDate(location.id)
            [
                    productCode      : StringEscapeUtils.escapeCsv(row?.product?.productCode),
                    productName      : row?.product.name ?: "",
                    productFamily    : product?.productFamily?.toString() ?: "",
                    category         : StringEscapeUtils.escapeCsv(product?.category?.name ?: ""),
                    formularies      : product?.productCatalogs?.join(", ") ?: "",
                    lotNumber        : StringEscapeUtils.escapeCsv(row?.inventoryItem.lotNumber ?: ""),
                    expirationDate   : row?.inventoryItem.expirationDate ? row?.inventoryItem.expirationDate.format(Constants.EXPIRATION_DATE_FORMAT) : "",
                    abcClassification: StringEscapeUtils.escapeCsv(row?.product.getAbcClassification(location.id) ?: ""),
                    binLocation      : StringEscapeUtils.escapeCsv(row?.binLocation?.name ?: ""),
                    status           : g.message(code: "binLocationSummary.${row?.status}.label"),
                    lastInventoryDate: latestInventoryDate ? latestInventoryDate.format(Constants.EXPIRATION_DATE_FORMAT) : "",
                    quantityOnHand   : row?.quantity ?: 0,
            ]
        }

        render([data: rows, location: [id: location.id, name: location.name]] as JSON)
    }

    /**
     * Shipment checklist data backing the React print report screens
     * (printShippingReport, printPickListReport,
     * printPaginatedPackingListReport). Mirrors what the legacy GSPs pulled
     * off ChecklistReportCommand after reportService.generateShippingReport(),
     * plus the bin-location availability map used by the pick list report.
     */
    def shippingReport() {
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            response.status = 404
            render([errorMessage: "Shipment with id ${params.id} not found"] as JSON)
            return
        }

        ChecklistReportCommand command = new ChecklistReportCommand(shipment: shipment)
        reportService.generateShippingReport(command)
        Map binLocationMap = inventoryService.getBinLocations(shipment)

        List entries = command.checklistReportEntryList.collect { entry ->
            def shipmentItem = entry.shipmentItem
            def inventoryItem = shipmentItem?.inventoryItem
            def product = inventoryItem?.product ?: shipmentItem?.product
            def container = shipmentItem?.container
            def expirationDate = inventoryItem?.expirationDate ?: shipmentItem?.expirationDate
            def recipient = shipmentItem?.recipient?.name
                    ?: container?.recipient?.name
                    ?: shipmentItem?.shipment?.recipient?.name
            [
                    container        : container ? [
                            id                 : container.id,
                            name               : container.name,
                            parentContainerName: container.parentContainer?.name,
                    ] : null,
                    productCode      : product?.productCode,
                    productName      : product?.displayNameOrDefaultName,
                    coldChain        : product?.coldChain ?: false,
                    unitOfMeasure    : product?.unitOfMeasure,
                    lotNumber        : inventoryItem?.lotNumber ?: shipmentItem?.lotNumber,
                    expirationDate   : expirationDate ? expirationDate.format(Constants.DEFAULT_MONTH_YEAR_DATE_FORMAT) : null,
                    quantity         : shipmentItem?.quantity,
                    recipient        : recipient,
                    binLocationPicked: shipmentItem?.binLocation?.name,
                    binLocations     : (binLocationMap[inventoryItem] ?: []).collect {
                        [binLocation: it?.binLocation?.name, quantity: it?.quantity]
                    },
            ]
        }

        render([data: [
                shipment: [
                        id                  : shipment.id,
                        name                : shipment.name,
                        shipmentNumber      : shipment.shipmentNumber,
                        expectedShippingDate: shipment.expectedShippingDate?.format("MMM dd, yyyy hh:mma z"),
                        expectedDeliveryDate: shipment.expectedDeliveryDate?.format("MMM dd, yyyy hh:mma z"),
                        origin              : [id: shipment.origin?.id, name: shipment.origin?.name],
                        destination         : [id: shipment.destination?.id, name: shipment.destination?.name],
                        licensePlateNumber  : shipment.getReferenceNumber("License Plate Number")?.identifier,
                ],
                printedBy: session?.user?.name,
                entries  : entries,
        ]] as JSON)
    }
}
