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
import grails.gorm.transactions.Transactional
import grails.validation.ValidationException
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Location
import org.pih.warehouse.core.User
import org.pih.warehouse.inventory.InventoryItem
import org.pih.warehouse.inventory.Transaction
import org.pih.warehouse.inventory.TransactionCode
import org.pih.warehouse.inventory.TransactionEntry
import org.pih.warehouse.inventory.TransactionSource
import org.pih.warehouse.inventory.TransactionType
import org.pih.warehouse.product.Product

import java.text.SimpleDateFormat

/**
 * Record-stock transactions for the React createTransaction screen
 * (mirrors InventoryController.saveAdjustmentTransaction,
 * saveDebitTransaction and saveCreditTransaction).
 */
@Transactional
class TransactionApiController {

    def inventoryService
    def adjustInventoryService
    def transactionIdentifierService

    def create() {
        Location location = Location.get(params.locationId ?: session?.warehouse?.id)
        if (!location) {
            throw new IllegalArgumentException("Cannot create transactions without a location - sign in or provide locationId as a request parameter")
        }

        def json = request.JSON
        TransactionType transactionType = TransactionType.get(json.transactionTypeId as String)
        if (!transactionType) {
            throw new IllegalArgumentException("Invalid or missing transactionTypeId")
        }

        Transaction transaction = new Transaction()
        transaction.transactionType = transactionType
        transaction.inventory = location.inventory
        transaction.transactionDate = json.transactionDate ?
                new SimpleDateFormat("MM/dd/yyyy HH:mm").parse(json.transactionDate as String) : new Date()
        transaction.comment = json.comment ?: null
        transaction.createdBy = User.load(session.user.id)

        boolean isAdjustment = transactionType.id in [
                Constants.ADJUSTMENT_CREDIT_TRANSACTION_TYPE_ID,
                Constants.ADJUSTMENT_DEBIT_TRANSACTION_TYPE_ID,
        ]

        if (transactionType.id == Constants.TRANSFER_OUT_TRANSACTION_TYPE_ID && json.destinationId) {
            transaction.destination = Location.get(json.destinationId as String)
        }
        if (transactionType.id == Constants.TRANSFER_IN_TRANSACTION_TYPE_ID && json.sourceId) {
            transaction.source = Location.get(json.sourceId as String)
        }

        def entries = json.entries ?: []
        if (!entries) {
            throw new IllegalArgumentException("Transaction entries must not be empty")
        }

        if (transactionType.transactionCode == TransactionCode.DEBIT && !isAdjustment) {
            transaction.transactionNumber = transactionIdentifierService.generate(transaction)
        }

        List<Product> products = []
        entries.each { entry ->
            InventoryItem inventoryItem = resolveInventoryItem(entry)
            if (!inventoryItem) {
                throw new IllegalArgumentException("Unable to resolve inventory item for entry ${entry}")
            }

            Integer quantity = entry.quantity as Integer
            if (quantity == null || quantity == 0) {
                return
            }

            if (transactionType.transactionCode == TransactionCode.DEBIT && !isAdjustment) {
                Integer onHandQuantity = inventoryService.getQuantity(location, inventoryItem.product, inventoryItem.lotNumber)
                if (quantity > onHandQuantity) {
                    throw new IllegalArgumentException("Quantity for lot number ${inventoryItem.lotNumber ?: 'default'} cannot be greater than on-hand quantity")
                }
            }
            if ((transactionType.transactionCode == TransactionCode.CREDIT || isAdjustment) && quantity < 0) {
                throw new IllegalArgumentException("Quantity for lot number ${inventoryItem.lotNumber ?: 'default'} must not be negative")
            }

            TransactionEntry transactionEntry = new TransactionEntry()
            transactionEntry.inventoryItem = inventoryItem
            transactionEntry.product = inventoryItem.product
            transactionEntry.quantity = quantity
            transactionEntry.binLocation = entry.binLocationId ? Location.get(entry.binLocationId as String) : null
            transactionEntry.comments = entry.comment ?: null
            transactionEntry.reasonCode = entry.reasonCode ?: null
            transaction.addToTransactionEntries(transactionEntry)
            products << inventoryItem.product
        }

        if (!transaction.transactionEntries) {
            throw new IllegalArgumentException("Transaction entries must not be empty")
        }

        if (isAdjustment) {
            TransactionSource transactionSource =
                    adjustInventoryService.createAdjustInventoryTransactionSource(location)
            transaction.transactionSource = transactionSource
        }

        if (!transaction.validate() || !transaction.save(flush: true)) {
            throw new ValidationException("Invalid transaction", transaction.errors)
        }

        render([data: [
                id               : transaction.id,
                transactionNumber: transaction.transactionNumber,
                transactionType  : [id: transactionType.id, name: transactionType.name],
                productIds       : products*.id.unique(),
        ]] as JSON)
    }

    private InventoryItem resolveInventoryItem(def entry) {
        if (entry.inventoryItemId) {
            return InventoryItem.get(entry.inventoryItemId as String)
        }
        if (entry.productId) {
            Product product = Product.get(entry.productId as String)
            if (!product) {
                return null
            }
            String lotNumber = entry.lotNumber ?: null
            Date expirationDate = entry.expirationDate ?
                    new SimpleDateFormat("MM/dd/yyyy").parse(entry.expirationDate as String) : null
            return inventoryService.findOrCreateInventoryItem(product, lotNumber, expirationDate)
        }
        return null
    }
}
