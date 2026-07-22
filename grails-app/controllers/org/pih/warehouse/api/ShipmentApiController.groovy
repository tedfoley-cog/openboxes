package org.pih.warehouse.api

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.validation.ValidationException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest
import util.FileUtil

import org.pih.warehouse.core.Comment
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Document
import org.pih.warehouse.core.DocumentCode
import org.pih.warehouse.core.DocumentType
import org.pih.warehouse.core.Event
import org.pih.warehouse.core.EventType
import org.pih.warehouse.core.Location
import org.pih.warehouse.core.Person
import org.pih.warehouse.core.RoleType
import org.pih.warehouse.core.User
import org.pih.warehouse.inventory.InventoryItem
import org.pih.warehouse.inventory.TransactionException
import org.pih.warehouse.shipping.Container
import org.pih.warehouse.shipping.ContainerType
import org.pih.warehouse.shipping.ItemCommand
import org.pih.warehouse.shipping.ItemListCommand
import org.pih.warehouse.shipping.ReferenceNumber
import org.pih.warehouse.shipping.ReferenceNumberType
import org.pih.warehouse.shipping.Shipment
import org.pih.warehouse.shipping.ShipmentException
import org.pih.warehouse.shipping.ShipmentItem
import org.pih.warehouse.shipping.ShipmentItemException
import org.pih.warehouse.shipping.ShipmentMethod
import org.pih.warehouse.shipping.ShipmentType
import org.pih.warehouse.shipping.ShipmentWorkflow
import org.pih.warehouse.shipping.Shipper

/**
 * REST endpoints backing the React screens that replaced the legacy
 * createShipmentWorkflow webflow GSPs (Phase 2, Batch 19):
 * enterShipmentDetails, enterTrackingDetails, enterContainerDetails,
 * pickShipmentItems and sendShipment.
 */
@Transactional
class ShipmentApiController {

    def shipmentService
    def inventoryService
    def locationService
    def userService
    def mailService
    def documentService
    def shipmentEventManager

    /**
     * Options needed by the create shipment wizard screens (mirrors the
     * selectShipmentOrigin/selectShipmentDestination/selectShipper taglibs
     * and the ShipmentType/ContainerType select boxes on the legacy GSPs).
     */
    def wizardOptions() {
        if (!requireManager()) {
            return
        }
        render([data: [
                shipmentTypes: ShipmentType.list().collect { [id: it.id, name: it.name] },
                origins      : locationService.getShipmentOrigins().sort { it?.name?.toLowerCase() }.collect {
                    [id: it.id, name: it.name, locationType: it.locationType?.name]
                },
                destinations : locationService.getShipmentDestinations().sort { it?.name?.toLowerCase() }.collect {
                    [id: it.id, name: it.name, locationType: it.locationType?.name]
                },
                shippers     : Shipper.list().sort { it?.name?.toLowerCase() }.collect { [id: it.id, name: it.name] },
                containerTypes: ContainerType.list().collect { [id: it.id, name: it.name] },
        ]] as JSON)
    }

    /**
     * Shipment details + workflow metadata for the wizard screens.
     */
    def read() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        render([data: getWizardDetails(shipment)] as JSON)
    }

    /**
     * Mirrors the enterShipmentDetails "next"/"save" transitions: creates a
     * new shipment (no id) or updates the shipment details.
     */
    def saveDetails() {
        def jsonObject = request.JSON
        Shipment shipment
        if (params.id) {
            shipment = Shipment.get(params.id)
            if (!shipment) {
                renderNotFound()
                return
            }
        } else {
            shipment = new Shipment()
        }
        try {
            if (jsonObject.containsKey("name")) {
                shipment.name = jsonObject.name
            }
            if (jsonObject.containsKey("shipmentTypeId")) {
                shipment.shipmentType = jsonObject.shipmentTypeId ? ShipmentType.get(jsonObject.shipmentTypeId) : null
            }
            if (jsonObject.containsKey("originId")) {
                shipment.origin = jsonObject.originId ? Location.get(jsonObject.originId) : null
            }
            if (jsonObject.containsKey("destinationId")) {
                shipment.destination = jsonObject.destinationId ? Location.get(jsonObject.destinationId) : null
            }
            if (jsonObject.containsKey("expectedShippingDate")) {
                shipment.expectedShippingDate = parseDate(jsonObject.expectedShippingDate)
            }
            if (jsonObject.containsKey("expectedDeliveryDate")) {
                shipment.expectedDeliveryDate = parseDate(jsonObject.expectedDeliveryDate)
            }
            if (shipment.hasErrors() || !shipment.validate()) {
                renderValidationErrors(shipment)
                return
            }
            shipmentService.saveShipment(shipment)
        } catch (ValidationException e) {
            renderValidationException(e)
            return
        } catch (Exception e) {
            renderError(e.message)
            return
        }
        response.status = params.id ? 200 : 201
        render([data: getWizardDetails(shipment)] as JSON)
    }

    /**
     * Mirrors the enterTrackingDetails "next"/"save" transitions, including
     * the manual binding of the shipper (nested in shipmentMethod) and the
     * reference numbers declared on the shipment workflow.
     */
    def saveTracking() {
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        def jsonObject = request.JSON
        ShipmentWorkflow shipmentWorkflow = shipmentService.getShipmentWorkflow(shipment)
        try {
            if (jsonObject.containsKey("carrierId")) {
                shipment.carrier = jsonObject.carrierId ? Person.get(jsonObject.carrierId) : null
            }
            if (jsonObject.containsKey("recipientId")) {
                shipment.recipient = jsonObject.recipientId ? Person.get(jsonObject.recipientId) : null
            }
            if (jsonObject.containsKey("statedValue")) {
                shipment.statedValue = jsonObject.statedValue != null && jsonObject.statedValue != "" ?
                        new BigDecimal(jsonObject.statedValue.toString()).floatValue() : null
            }
            if (jsonObject.containsKey("totalValue")) {
                shipment.totalValue = jsonObject.totalValue != null && jsonObject.totalValue != "" ?
                        new BigDecimal(jsonObject.totalValue.toString()).floatValue() : null
            }
            if (jsonObject.containsKey("additionalInformation")) {
                shipment.additionalInformation = jsonObject.additionalInformation
            }
            if (jsonObject.containsKey("shipperId")) {
                // mirrors bindShipper: no shipper input removes the entire shipment method
                if (jsonObject.shipperId) {
                    if (!shipment.shipmentMethod) {
                        shipment.shipmentMethod = new ShipmentMethod()
                    }
                    shipment.shipmentMethod.shipper = Shipper.get(jsonObject.shipperId)
                    shipment.shipmentMethod.trackingNumber = jsonObject.trackingNumber
                } else {
                    shipment.shipmentMethod = null
                }
            }
            if (jsonObject.containsKey("referenceNumbers")) {
                bindReferenceNumbers(shipment, shipmentWorkflow, jsonObject.referenceNumbers)
            }
            if (shipment.hasErrors() || !shipment.validate()) {
                renderValidationErrors(shipment)
                return
            }
            shipmentService.saveShipment(shipment)
        } catch (ValidationException e) {
            renderValidationException(e)
            return
        } catch (Exception e) {
            renderError(e.message)
            return
        }
        render([data: getWizardDetails(shipment)] as JSON)
    }

    /**
     * Containers and items for the packing screen (enterContainerDetails).
     */
    def packing() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        render([data: getWizardDetails(shipment) + [
                containers   : shipment.findAllParentContainers()?.sort { it.sortOrder }?.collect { container ->
                    getContainerDetails(shipment, container)
                } ?: [],
                unpackedItems: shipment.unpackedShipmentItems?.collect { getItemDetails(it) } ?: [],
        ]] as JSON)
    }

    /**
     * Mirrors the addContainers flow event: creates one container per line of
     * the containerText for the given container type.
     */
    def createContainers() {
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        def jsonObject = request.JSON
        try {
            shipmentService.createContainers(shipment.id, jsonObject.parentContainerId,
                    jsonObject.containerTypeId, jsonObject.containerText)
        } catch (ShipmentException e) {
            renderError(e.message)
            return
        }
        response.status = 201
        render([data: [id: shipment.id]] as JSON)
    }

    /**
     * Mirrors the deleteContainers / deleteContainersAndItems flow events.
     * When deleteItems is false a container that still contains items cannot
     * be deleted (like the legacy deleteContainers event).
     */
    def deleteContainer() {
        Shipment shipment = Shipment.get(params.id)
        Container container = Container.get(params.containerId)
        if (!shipment || !container || container.shipment?.id != shipment.id) {
            renderNotFound()
            return
        }
        boolean deleteItems = params.boolean("deleteItems", false)
        try {
            shipmentService.deleteContainers(shipment.id, [container.id], deleteItems)
        } catch (ShipmentException e) {
            renderError(e.message)
            return
        }
        render([data: [id: shipment.id]] as JSON)
    }

    /**
     * Mirrors the addShipmentItem flow event: adds an inventory item to the
     * shipment (optionally inside a container).
     */
    def addItem() {
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        def jsonObject = request.JSON
        try {
            shipmentService.addToShipmentItems(shipment.id, jsonObject.containerId,
                    jsonObject.inventoryItemId, jsonObject.quantity as Integer)
        } catch (ValidationException e) {
            renderValidationException(e)
            return
        } catch (Exception e) {
            renderError(e.message)
            return
        }
        response.status = 201
        render([data: [id: shipment.id]] as JSON)
    }

    /**
     * Updates a shipment item (quantity and/or container), mirroring the
     * updateShipmentItem and moveItemToContainer flow events.
     */
    def updateItem() {
        Shipment shipment = Shipment.get(params.id)
        ShipmentItem shipmentItem = ShipmentItem.get(params.itemId)
        if (!shipment || !shipmentItem || shipmentItem.shipment?.id != shipment.id) {
            renderNotFound()
            return
        }
        def jsonObject = request.JSON
        try {
            if (jsonObject.containsKey("quantity")) {
                shipmentItem.quantity = jsonObject.quantity as Integer
            }
            if (jsonObject.containsKey("containerId")) {
                shipmentItem.container = jsonObject.containerId ? Container.get(jsonObject.containerId) : null
            }
            if (shipmentService.validateShipmentItem(shipmentItem)) {
                shipmentService.saveShipmentItem(shipmentItem)
            }
        } catch (ValidationException e) {
            shipmentItem.discard()
            renderValidationException(e)
            return
        } catch (Exception e) {
            shipmentItem.discard()
            renderError(e.message)
            return
        }
        render([data: getItemDetails(shipmentItem)] as JSON)
    }

    /**
     * Mirrors the deleteItem / deleteShipmentItem flow events.
     */
    def deleteItem() {
        Shipment shipment = Shipment.get(params.id)
        ShipmentItem shipmentItem = ShipmentItem.get(params.itemId)
        if (!shipment || !shipmentItem || shipmentItem.shipment?.id != shipment.id) {
            renderNotFound()
            return
        }
        try {
            shipmentService.deleteShipmentItem(shipmentItem)
        } catch (Exception e) {
            renderError(e.message)
            return
        }
        render([data: [id: shipment.id]] as JSON)
    }

    /**
     * Picklist data for the pickShipmentItems screen: shipment items in
     * picklist order plus, for each product, the quantity available in each
     * bin location at the origin.
     */
    def picklist() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        Location location = Location.get(session.warehouse.id)
        def sortedItems = shipment.sortShipmentItems() ?: []
        def binLocationsByProduct = [:]
        sortedItems.collect { it.inventoryItem?.product }.unique().findAll { it }.each { product ->
            def binLocations = inventoryService.getProductQuantityByBinLocation(location, product)
            binLocationsByProduct[product.id] = binLocations.collect { entry ->
                [
                        binLocation  : entry.binLocation ? [id: entry.binLocation.id, name: entry.binLocation.name] : null,
                        inventoryItem: entry.inventoryItem ? [
                                id            : entry.inventoryItem.id,
                                lotNumber     : entry.inventoryItem.lotNumber,
                                expirationDate: entry.inventoryItem.expirationDate?.format("yyyy-MM-dd"),
                        ] : null,
                        quantity     : entry.quantity,
                ]
            }
        }
        render([data: getWizardDetails(shipment) + [
                shipmentItems        : sortedItems.collect { getItemDetails(it) },
                binLocationsByProduct: binLocationsByProduct,
                isOrigin             : shipment.origin?.id == session.warehouse.id,
        ]] as JSON)
    }

    /**
     * Mirrors the pickShipmentItem flow event: assigns the bin location,
     * inventory item and quantity of a picklist row.
     */
    def pickItem() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        ShipmentItem shipmentItem = ShipmentItem.get(params.itemId)
        if (!shipment || !shipmentItem || shipmentItem.shipment?.id != shipment.id) {
            renderNotFound()
            return
        }
        def jsonObject = request.JSON
        try {
            InventoryItem inventoryItem = jsonObject.inventoryItemId ?
                    InventoryItem.get(jsonObject.inventoryItemId) : null
            if (!inventoryItem) {
                renderError("Inventory item is a required field")
                return
            }
            shipmentItem.inventoryItem = inventoryItem
            shipmentItem.binLocation = jsonObject.binLocationId ? Location.get(jsonObject.binLocationId) : null
            shipmentItem.quantity = jsonObject.quantity as Integer
            if (shipmentService.validateShipmentItem(shipmentItem)) {
                shipmentItem.save(flush: true)
            }
        } catch (ValidationException e) {
            shipmentItem.discard()
            renderValidationException(e)
            return
        } catch (Exception e) {
            shipmentItem.discard()
            renderError(e.message)
            return
        }
        render([data: getItemDetails(shipmentItem)] as JSON)
    }

    /**
     * Mirrors the splitShipmentItem2 flow event: clones the shipment item
     * with quantity zero so a second bin/lot can be picked.
     */
    def splitItem() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        ShipmentItem shipmentItem = ShipmentItem.get(params.itemId)
        if (!shipment || !shipmentItem || shipmentItem.shipment?.id != shipment.id) {
            renderNotFound()
            return
        }
        ShipmentItem shipmentItemClone = shipmentItem.cloneShipmentItem()
        shipmentItemClone.quantity = 0
        shipment.addToShipmentItems(shipmentItemClone)
        shipment.save(flush: true)
        render([data: getItemDetails(shipmentItemClone)] as JSON)
    }

    /**
     * Mirrors the validatePicklist flow event. Returns per-item validation
     * errors keyed by shipment item id.
     */
    def validatePicklist() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        try {
            shipmentService.validatePicklist(shipment)
        } catch (ValidationException e) {
            render([data: [valid: false, errors: e.errors.allErrors.collect { g.message(error: it) }]] as JSON)
            return
        }
        render([data: [valid: true, errors: []]] as JSON)
    }

    /**
     * Mirrors the clearPicklist flow event (superuser only, like the legacy
     * screen's isSuperuser-guarded button).
     */
    def clearPicklist() {
        if (!userService.isSuperuser(session?.user)) {
            response.status = 403
            render([errorCode: 403, errorMessage: "Only superusers can clear the picklist"] as JSON)
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        shipmentService.clearPicklist(shipment)
        render([data: [id: shipment.id]] as JSON)
    }

    /**
     * Mirrors the sendShipmentAction flow state: validates the picklist,
     * sends the shipment (debiting stock) and triggers the notification
     * emails to the selected recipients.
     */
    def send() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        def jsonObject = request.JSON
        User userInstance = User.get(session.user.id)
        Location warehouseInstance = Location.get(session.warehouse.id)
        try {
            shipmentService.validatePicklist(shipment)
            Date actualShippingDate = jsonObject.actualShippingDate ?
                    Date.parse("yyyy-MM-dd HH:mm", jsonObject.actualShippingDate as String) : new Date()
            boolean debitStockOnSend = jsonObject.containsKey("debitStockOnSend") ?
                    jsonObject.debitStockOnSend as Boolean : true
            shipmentService.sendShipment(shipment, jsonObject.comments as String, userInstance,
                    warehouseInstance, actualShippingDate, debitStockOnSend)
            // notification failures must never surface as a send failure
            // because the shipment has already been sent at this point
            try {
                def emailRecipients = new HashSet()
                jsonObject.emailRecipientIds?.each { recipientId ->
                    Person recipient = Person.get(recipientId)
                    if (recipient && recipient.email) {
                        emailRecipients.add(recipient)
                    }
                }
                triggerSendShipmentEmails(shipment, userInstance, emailRecipients)
            } catch (Exception e) {
                log.error("Error triggering send shipment emails: ${e.message}", e)
            }
        } catch (ValidationException e) {
            renderValidationException(e)
            return
        } catch (ShipmentException e) {
            renderError(e.message)
            return
        } catch (TransactionException e) {
            renderError(e.message)
            return
        } catch (Exception e) {
            renderError(e.message)
            return
        }
        render([data: getWizardDetails(shipment)] as JSON)
    }

    /**
     * Mirrors the legacy ShipmentController.deleteShipment POST branch for the
     * migrated delete shipment screen.
     */
    def delete() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        try {
            shipmentService.deleteShipment(shipment)
        } catch (Exception e) {
            renderError(e.message)
            return
        }
        render(status: 204)
    }

    /**
     * Mirrors the legacy ShipmentController.saveComment action for the
     * migrated add comment screen.
     */
    def createComment() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        def jsonObject = request.JSON
        User sender = User.get(session.user.id)
        User recipient = jsonObject.recipient?.id ? User.get(jsonObject.recipient.id) : null
        Comment comment = new Comment(comment: jsonObject.comment ?: null, sender: sender, recipient: recipient)
        comment.validate()
        if (comment.hasErrors()) {
            response.status = 400
            render([errorCode: 400, errorMessage: "Validation errors",
                    errors: comment.errors.allErrors.collect { g.message(error: it) }] as JSON)
            return
        }
        comment.save()
        shipment.addToComments(comment)
        if (shipment.hasErrors() || !shipment.save(flush: true)) {
            renderValidationErrors(shipment)
            return
        }
        response.status = 201
        render([data: [
                id       : comment.id,
                comment  : comment.comment,
                sender   : comment.sender ? [id: comment.sender.id, name: comment.sender.name] : null,
                recipient: comment.recipient ? [id: comment.recipient.id, name: comment.recipient.name] : null,
        ]] as JSON)
    }

    /**
     * Non-template document types for the migrated add document screen
     * (mirrors documentService.getNonTemplateDocumentTypes used by the legacy
     * ShipmentController.addDocument action).
     */
    def documentTypes() {
        if (!requireManager()) {
            return
        }
        List<DocumentType> documentTypeList = documentService.getNonTemplateDocumentTypes().sort { it.name }
        render([data: documentTypeList.collect {
            [id: it.id, value: it.id, label: it.name]
        }] as JSON)
    }

    /**
     * Mirrors the shipment branch of DocumentController.uploadDocument for the
     * migrated add document screen. Accepts either an uploaded file or a URL
     * (fileUri), like the legacy form.
     */
    def uploadDocument() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        MultipartFile file = request instanceof MultipartHttpServletRequest ? request.getFile("fileContents") : null
        String fileUri = params.fileUri
        if (!file?.size && !fileUri) {
            renderError(g.message(code: 'document.documentCannotBeEmpty.message') as String)
            return
        }
        String typeId = params.typeId ?: Constants.DEFAULT_DOCUMENT_TYPE_ID
        DocumentType documentType = DocumentType.get(typeId)
        Document documentInstance
        if (file?.size) {
            if (!Document.isAllowedFile(file.originalFilename, file.contentType, file.inputStream)) {
                renderError(g.message(code: 'document.uploadNotAllowed.message',
                        args: [Document.allowedExtensions().join(', ')]) as String)
                return
            }
            if (file.size >= 10 * 1024 * 1000) {
                renderError(g.message(code: 'document.documentTooLarge.message') as String)
                return
            }
            documentInstance = new Document(
                    size: file.size,
                    name: params.name ?: file.originalFilename,
                    filename: file.originalFilename,
                    fileContents: file.bytes,
                    contentType: file.contentType,
                    extension: file.originalFilename ? FileUtil.getExtension(file.originalFilename) : null,
                    documentNumber: params.documentNumber,
                    documentType: documentType)
        } else {
            documentInstance = new Document(
                    size: 0,
                    name: params.name ?: fileUri,
                    fileUri: fileUri,
                    documentNumber: params.documentNumber,
                    documentType: documentType)
        }
        documentInstance.validate()
        List<DocumentCode> forbiddenDocumentCodes = DocumentCode.templateList()
        if (documentType && forbiddenDocumentCodes.contains(documentType.documentCode)) {
            documentInstance.errors.reject("documentType", "Template types are not allowed for this document upload")
        }
        if (documentInstance.hasErrors()) {
            transactionStatus.setRollbackOnly()
            response.status = 400
            render([errorCode: 400, errorMessage: "Validation errors",
                    errors: documentInstance.errors.allErrors.collect { g.message(error: it) }] as JSON)
            return
        }
        shipment.addToDocuments(documentInstance)
        if (shipment.hasErrors() || !shipment.save(flush: true)) {
            transactionStatus.setRollbackOnly()
            renderValidationErrors(shipment)
            return
        }
        response.status = 201
        render([data: [
                id            : documentInstance.id,
                name          : documentInstance.name,
                filename      : documentInstance.filename,
                documentNumber: documentInstance.documentNumber,
                documentType  : documentInstance.documentType ? [id: documentInstance.documentType.id, name: documentInstance.documentType.name] : null,
                fileUri       : documentInstance.fileUri,
        ]] as JSON)
    }

    /**
     * Select options for the migrated edit event screen (mirrors the
     * EventType.list() and Location.list() selects on the legacy GSP).
     */
    def eventOptions() {
        if (!requireManager()) {
            return
        }
        render([data: [
                eventTypes: EventType.list().collect { [id: it.id, name: it.name] },
                locations : Location.list().collect { [id: it.id, name: it.name] },
        ]] as JSON)
    }

    /**
     * Event details for the migrated edit event screen.
     */
    def readEvent() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        Event event = Event.get(params.eventId)
        if (!event) {
            response.status = 404
            render([errorCode: 404, errorMessage: "Event ${params.eventId} not found"] as JSON)
            return
        }
        render([data: getEventDetails(event)] as JSON)
    }

    /**
     * Mirrors the legacy ShipmentController.saveEvent action: creates a new
     * event (no eventId) via the shipment event manager or updates an
     * existing one.
     */
    def saveEvent() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        def jsonObject = request.JSON
        Event event = params.eventId ? Event.get(params.eventId) : new Event()
        if (!event) {
            response.status = 404
            render([errorCode: 404, errorMessage: "Event ${params.eventId} not found"] as JSON)
            return
        }
        if (jsonObject.containsKey("eventTypeId") && !event.id) {
            event.eventType = jsonObject.eventTypeId ? EventType.get(jsonObject.eventTypeId) : null
        }
        if (jsonObject.containsKey("eventDate")) {
            event.eventDate = jsonObject.eventDate ?
                    Date.parse("yyyy-MM-dd HH:mm", jsonObject.eventDate as String) : null
        }
        if (jsonObject.containsKey("eventLocationId")) {
            event.eventLocation = jsonObject.eventLocationId ? Location.get(jsonObject.eventLocationId) : null
        }
        event.validate()
        if (event.hasErrors()) {
            response.status = 400
            render([errorCode: 400, errorMessage: "Validation errors",
                    errors: event.errors.allErrors.collect { g.message(error: it) }] as JSON)
            return
        }
        try {
            if (params.eventId) {
                event.save(flush: true)
            } else {
                shipmentEventManager.createEvent(shipment, event)
                shipment.save(flush: true)
            }
        } catch (ValidationException e) {
            renderValidationException(e)
            return
        }
        response.status = params.eventId ? 200 : 201
        render([data: getEventDetails(event)] as JSON)
    }

    /**
     * Mirrors the legacy ShipmentController.deleteEvent action (rolls the
     * event back through the shipment event manager).
     */
    def deleteEvent() {
        if (!requireManager()) {
            return
        }
        Shipment shipment = Shipment.get(params.id)
        if (!shipment) {
            renderNotFound()
            return
        }
        Event event = Event.get(params.eventId)
        if (!event) {
            response.status = 404
            render([errorCode: 404, errorMessage: "Event ${params.eventId} not found"] as JSON)
            return
        }
        shipmentEventManager.rollbackEvent(shipment, event)
        shipment.save()
        render(status: 204)
    }

    /**
     * Candidate inventory items and pending shipments/containers for the
     * migrated add-to-shipment screen (mirrors
     * shipmentService.getAddToShipmentCommand and the selectContainer taglib).
     */
    def addToShipmentCandidates() {
        if (!requireManager()) {
            return
        }
        List<String> productIds = params.list("product.id").collect { String.valueOf(it) }
        Location location = Location.get(session.warehouse.id)
        ItemListCommand command = shipmentService.getAddToShipmentCommand(productIds, location)
        List<Shipment> pendingShipments = shipmentService.getPendingShipments(location)
        render([data: [
                items           : command.items.collect { ItemCommand item ->
                    [
                            inventoryItem    : item.inventoryItem ? [
                                    id            : item.inventoryItem.id,
                                    lotNumber     : item.inventoryItem.lotNumber,
                                    expirationDate: item.inventoryItem.expirationDate?.format("yyyy-MM-dd"),
                            ] : null,
                            product          : item.product ? [
                                    id         : item.product.id,
                                    productCode: item.product.productCode,
                                    name       : item.product.name,
                            ] : null,
                            lotNumber        : item.lotNumber,
                            quantityOnHand   : item.quantityOnHand ?: 0,
                            quantityShipping : item.quantityShipping ?: 0,
                            quantityReceiving: item.quantityReceiving ?: 0,
                    ]
                },
                pendingShipments: pendingShipments.collect { Shipment shipment ->
                    [
                            id                  : shipment.id,
                            name                : shipment.name,
                            shipmentNumber      : shipment.shipmentNumber,
                            destination         : shipment.destination ? [id: shipment.destination.id, name: shipment.destination.name] : null,
                            expectedShippingDate: shipment.expectedShippingDate?.format("yyyy-MM-dd"),
                            looseItemCount      : shipment.shipmentItems?.count { it.container == null } ?: 0,
                            containers          : shipment.containers?.collect { Container container ->
                                [
                                        id       : container.id,
                                        name     : container.name,
                                        itemCount: shipment.shipmentItems?.count { it.container?.id == container.id } ?: 0,
                                ]
                            } ?: [],
                    ]
                },
        ]] as JSON)
    }

    /**
     * Mirrors the legacy ShipmentController.addToShipmentPost action: adds
     * the selected inventory item quantities to a pending shipment/container.
     */
    def addToShipment() {
        if (!requireManager()) {
            return
        }
        def jsonObject = request.JSON
        Shipment shipment = jsonObject.shipmentId ? Shipment.get(jsonObject.shipmentId) : null
        if (!shipment) {
            renderError(g.message(code: 'addToShipment.container.invalid') as String)
            return
        }
        Container container = jsonObject.containerId ? Container.get(jsonObject.containerId) : null
        ItemListCommand command = new ItemListCommand()
        jsonObject.items?.each { itemInput ->
            ItemCommand item = new ItemCommand()
            item.inventoryItem = itemInput.inventoryItemId ? InventoryItem.get(itemInput.inventoryItemId) : null
            item.product = item.inventoryItem?.product
            item.lotNumber = item.inventoryItem?.lotNumber
            item.quantity = itemInput.quantity ? itemInput.quantity as Integer : 0
            item.shipment = shipment
            item.container = container
            command.items << item
        }
        try {
            boolean atLeastOneUpdate = shipmentService.addToShipment(command)
            render([data: [
                    atLeastOneUpdate: atLeastOneUpdate,
                    shipmentId      : shipment.id,
                    containerId     : container?.id,
            ]] as JSON)
        } catch (ShipmentItemException e) {
            response.status = 400
            render([errorCode: 400, errorMessage: "Validation errors",
                    errors: e.shipmentItem.errors.allErrors.collect { g.message(error: it) }] as JSON)
        } catch (ValidationException e) {
            renderValidationException(e)
        }
    }

    private Map getEventDetails(Event event) {
        [
                id           : event.id,
                eventType    : event.eventType ? [id: event.eventType.id, name: event.eventType.name] : null,
                eventDate    : event.eventDate?.format("yyyy-MM-dd HH:mm"),
                eventLocation: event.eventLocation ? [id: event.eventLocation.id, name: event.eventLocation.name] : null,
        ]
    }

    private void bindReferenceNumbers(Shipment shipment, ShipmentWorkflow workflow, referenceNumbersInput) {
        for (ReferenceNumberType type in workflow?.referenceNumberTypes) {
            ReferenceNumber referenceNumber = shipment.referenceNumbers?.find {
                it.referenceNumberType.id == type.id
            }
            def identifier = referenceNumbersInput?."${type.id}"
            if (identifier) {
                if (referenceNumber) {
                    referenceNumber.identifier = identifier
                } else {
                    shipment.addToReferenceNumbers(new ReferenceNumber(
                            identifier: identifier, referenceNumberType: type))
                }
            } else if (referenceNumber) {
                shipment.removeFromReferenceNumbers(referenceNumber)
                referenceNumber.delete()
            }
        }
    }

    private void triggerSendShipmentEmails(Shipment shipmentInstance, User userInstance, Set<Person> recipients) {
        if (!recipients) recipients = new HashSet<Person>()
        def adminList = userService.findUsersByRoleType(RoleType.ROLE_SHIPMENT_NOTIFICATION)
        adminList.each { adminUser ->
            if (adminUser?.email) {
                recipients.add(adminUser)
            }
        }
        if (userInstance) {
            recipients.add(userInstance)
        }
        if (!shipmentInstance.hasErrors()) {
            def shipmentName = "${shipmentInstance.name}"
            def shipmentType = "${shipmentInstance.shipmentType?.name}"
            def shipmentDate = "${shipmentInstance?.actualShippingDate?.format('MMMMM dd yyyy')}"
            def subject = "${g.message(code: 'shipment.hasBeenShipped.message', args: [shipmentType, shipmentName, shipmentDate])}"
            def body = g.render(template: "/email/shipmentShipped", model: [shipmentInstance: shipmentInstance, userInstance: userInstance])
            def toList = recipients?.collect { it?.email }?.unique()
            try {
                mailService.sendHtmlMail(subject, body.toString(), toList)
            } catch (Exception e) {
                log.error "Error triggering send shipment emails " + e.message
            }
        }
    }

    private Map getWizardDetails(Shipment shipment) {
        ShipmentWorkflow shipmentWorkflow = shipmentService.getShipmentWorkflow(shipment)
        [
                id                  : shipment.id,
                name                : shipment.name,
                shipmentNumber      : shipment.shipmentNumber,
                status              : shipment.status?.code?.name(),
                hasShipped          : shipment.hasShipped(),
                shipmentType        : shipment.shipmentType ? [id: shipment.shipmentType.id, name: shipment.shipmentType.name] : null,
                origin              : shipment.origin ? [id: shipment.origin.id, name: shipment.origin.name, isWarehouse: shipment.origin.isWarehouse()] : null,
                destination         : shipment.destination ? [id: shipment.destination.id, name: shipment.destination.name] : null,
                expectedShippingDate: shipment.expectedShippingDate?.format("yyyy-MM-dd"),
                expectedDeliveryDate: shipment.expectedDeliveryDate?.format("yyyy-MM-dd"),
                actualShippingDate  : shipment.actualShippingDate?.format("yyyy-MM-dd HH:mm"),
                carrier             : shipment.carrier ? [id: shipment.carrier.id, name: shipment.carrier.name, email: shipment.carrier.email] : null,
                recipient           : shipment.recipient ? [id: shipment.recipient.id, name: shipment.recipient.name, email: shipment.recipient.email] : null,
                shipper             : shipment.shipmentMethod?.shipper ? [id: shipment.shipmentMethod.shipper.id, name: shipment.shipmentMethod.shipper.name] : null,
                trackingNumber      : shipment.shipmentMethod?.trackingNumber,
                statedValue         : shipment.statedValue,
                totalValue          : shipment.totalValue,
                additionalInformation: shipment.additionalInformation,
                referenceNumbers    : shipment.referenceNumbers?.collect {
                    [id: it.id, identifier: it.identifier, referenceNumberType: [id: it.referenceNumberType?.id, name: it.referenceNumberType?.name]]
                } ?: [],
                shipmentItemCount   : shipment.shipmentItems?.size() ?: 0,
                itemRecipients      : shipment.allShipmentItems?.findAll { it.recipient }?.collect { it.recipient }?.unique { it.id }?.collect {
                    [id: it.id, name: it.name, email: it.email]
                } ?: [],
                workflow            : shipmentWorkflow ? [
                        id                  : shipmentWorkflow.id,
                        name                : shipmentWorkflow.name,
                        excludedFields      : shipmentWorkflow.excludedFields?.split(",")?.toList() ?: [],
                        referenceNumberTypes: shipmentWorkflow.referenceNumberTypes?.collect { [id: it.id, name: it.name] } ?: [],
                ] : null,
        ]
    }

    private Map getContainerDetails(Shipment shipment, Container container) {
        [
                id             : container.id,
                name           : container.name,
                sortOrder      : container.sortOrder,
                containerType  : container.containerType ? [id: container.containerType.id, name: container.containerType.name] : null,
                itemCount      : container.shipmentItems?.size() ?: 0,
                shipmentItems  : container.shipmentItems?.collect { getItemDetails(it) } ?: [],
                childContainers: shipment.findAllChildContainers(container)?.sort()?.collect { child ->
                    [
                            id           : child.id,
                            name         : child.name,
                            containerType: child.containerType ? [id: child.containerType.id, name: child.containerType.name] : null,
                            itemCount    : child.shipmentItems?.size() ?: 0,
                            shipmentItems: child.shipmentItems?.collect { getItemDetails(it) } ?: [],
                    ]
                } ?: [],
        ]
    }

    private Map getItemDetails(ShipmentItem shipmentItem) {
        [
                id           : shipmentItem.id,
                quantity     : shipmentItem.quantity,
                product      : shipmentItem.inventoryItem?.product ? [
                        id           : shipmentItem.inventoryItem.product.id,
                        productCode  : shipmentItem.inventoryItem.product.productCode,
                        name         : shipmentItem.inventoryItem.product.name,
                        unitOfMeasure: shipmentItem.inventoryItem.product.unitOfMeasure,
                ] : null,
                inventoryItem: shipmentItem.inventoryItem ? [
                        id            : shipmentItem.inventoryItem.id,
                        lotNumber     : shipmentItem.inventoryItem.lotNumber,
                        expirationDate: shipmentItem.inventoryItem.expirationDate?.format("yyyy-MM-dd"),
                ] : null,
                binLocation  : shipmentItem.binLocation ? [id: shipmentItem.binLocation.id, name: shipmentItem.binLocation.name] : null,
                container    : shipmentItem.container ? [id: shipmentItem.container.id, name: shipmentItem.container.name] : null,
                recipient    : shipmentItem.recipient ? [id: shipmentItem.recipient.id, name: shipmentItem.recipient.name, email: shipmentItem.recipient.email] : null,
        ]
    }

    private Date parseDate(value) {
        return value ? Date.parse("yyyy-MM-dd", value as String) : null
    }

    /**
     * The legacy webflow controller required the manager role for every
     * action (RoleInterceptor treats all *Workflow controllers as manager
     * only). Actions whose names match the interceptor's change-action
     * prefixes (save*, create*, delete*, add*, update*) are already covered;
     * this guard applies the same requirement to the remaining actions,
     * including the read-only ones the legacy workflow also gated.
     */
    private boolean requireManager() {
        if (!userService.isUserManager(session?.user)) {
            response.status = 403
            render([errorCode: 403, errorMessage: "Manager role required"] as JSON)
            return false
        }
        return true
    }

    private void renderNotFound() {
        response.status = 404
        render([errorCode: 404, errorMessage: "Shipment ${params.id} not found"] as JSON)
    }

    private void renderError(String message) {
        response.status = 400
        render([errorCode: 400, errorMessage: message] as JSON)
    }

    private void renderValidationErrors(Shipment shipment) {
        response.status = 400
        render([errorCode: 400, errorMessage: "Validation errors",
                errors: shipment.errors.allErrors.collect { g.message(error: it) }] as JSON)
    }

    private void renderValidationException(ValidationException e) {
        response.status = 400
        render([errorCode: 400, errorMessage: "Validation errors",
                errors: e.errors.allErrors.collect { g.message(error: it) }] as JSON)
    }
}
