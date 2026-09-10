package org.pih.warehouse.importer.spec

import org.pih.warehouse.common.util.FileResourceUtil
import org.pih.warehouse.importer.InventoryLevelExcelImporter
import org.pih.warehouse.importer.spec.base.ImporterSpec

class InventoryLevelExcelImporterSpec extends ImporterSpec {

    void 'InventoryLevelExcelImporter can import successfully'() {
        given:
        File file = FileResourceUtil.getFile("import/inventoryLevels.xls")
        List<Map> expectedData = [
                [
                        productCode          : "00001",
                        productName          : "Advil 200mg",
                        facility             : "Boston",
                        status               : "ENABLED",
                        internalLocation     : "A1",
                        preferredBinLocation : "true",
                        replenishmentLocation: "B1",
                        abcClass             : "A",
                        minQuantity          : 10.0,
                        reorderQuantity      : 20.0,
                        maxQuantity          : 100.0,
                ],
                [
                        productCode          : "00002",
                        productName          : "Tylenol 325mg",
                        facility             : "Boston",
                        status               : "DISABLED",
                        internalLocation     : null,
                        preferredBinLocation : null,
                        replenishmentLocation: null,
                        abcClass             : "B",
                        minQuantity          : 5.0,
                        reorderQuantity      : 10.0,
                        maxQuantity          : 50.0,
                ],
                [
                        productCode          : "00003",
                        productName          : "Aspirin 20mg",
                        facility             : "Miami",
                        status               : "ENABLED",
                        internalLocation     : "C2",
                        preferredBinLocation : "false",
                        replenishmentLocation: null,
                        abcClass             : null,
                        minQuantity          : 0.0,
                        reorderQuantity      : 0.0,
                        maxQuantity          : 0.0,
                ],
                [
                        productCode          : "00004",
                        productName          : "Ibuprofen 400mg",
                        facility             : "Miami",
                        status               : "ENABLED",
                        internalLocation     : null,
                        preferredBinLocation : null,
                        replenishmentLocation: null,
                        abcClass             : "C",
                        minQuantity          : null,
                        reorderQuantity      : null,
                        maxQuantity          : null,
                ],
        ]

        when:
        InventoryLevelExcelImporter importer = new InventoryLevelExcelImporter(file.absolutePath)
        List<Map> actualData = importer.data

        then:
        assert actualData != null
        assert actualData.size() == 4
        for (i in 0..3) {
            assert actualData[i].productCode == expectedData[i].productCode
            assert actualData[i].productName == expectedData[i].productName
            assert actualData[i].facility == expectedData[i].facility
            assert actualData[i].status == expectedData[i].status
            assert actualData[i].internalLocation == expectedData[i].internalLocation
            assert actualData[i].preferredBinLocation == expectedData[i].preferredBinLocation
            assert actualData[i].replenishmentLocation == expectedData[i].replenishmentLocation
            assert actualData[i].abcClass == expectedData[i].abcClass
            assert actualData[i].minQuantity == expectedData[i].minQuantity
            assert actualData[i].reorderQuantity == expectedData[i].reorderQuantity
            assert actualData[i].maxQuantity == expectedData[i].maxQuantity
        }
    }
}
