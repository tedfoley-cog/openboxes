package org.pih.warehouse.importer.spec

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellValue
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.pih.warehouse.common.util.FileResourceUtil
import org.pih.warehouse.importer.InventoryLevelExcelImporter
import org.pih.warehouse.importer.spec.base.ImporterSpec

class InventoryLevelExcelImporterSpec extends ImporterSpec {

    void 'InventoryLevelExcelImporter can import successfully'() {
        given:
        File file = FileResourceUtil.getFile("import/inventoryLevels.xls")
        List<Map> expectedData = expectedData(file)

        when:
        InventoryLevelExcelImporter importer = new InventoryLevelExcelImporter(file.absolutePath)
        List<Map> actualData = importer.data

        then:
        assert actualData != null
        assert actualData.size() == expectedData.size()
        expectedData.eachWithIndex { Map expectedRow, int rowIndex ->
            expectedRow.each { String property, Object expectedValue ->
                assert actualData[rowIndex][property] == expectedValue:
                        "row ${rowIndex + 1}, ${property}: expected ${expectedValue} (${expectedValue?.class?.simpleName}), got ${actualData[rowIndex][property]} (${actualData[rowIndex][property]?.class?.simpleName})"
            }
        }
        assert actualData.every { row ->
            row.productCode == null || row.productCode instanceof String
            row.productName == null || row.productName instanceof String
            row.facility == null || row.facility instanceof String
            row.status == null || row.status instanceof String
            row.internalLocation == null || row.internalLocation instanceof String
            row.preferredBinLocation == null || row.preferredBinLocation instanceof String || row.preferredBinLocation instanceof Boolean
            row.replenishmentLocation == null || row.replenishmentLocation instanceof String
            row.abcClass == null || row.abcClass instanceof String
            row.minQuantity == null || row.minQuantity instanceof Number
            row.reorderQuantity == null || row.reorderQuantity instanceof Number
            row.maxQuantity == null || row.maxQuantity instanceof Number
        }
    }

    private List<Map> expectedData(File file) {
        Workbook workbook = WorkbookFactory.create(file)
        try {
            def sheet = workbook.getSheet("Sheet1")
            FormulaEvaluator evaluator = workbook.creationHelper.createFormulaEvaluator()
            return (1..sheet.lastRowNum)
                    .collect { int rowNumber ->
                        def row = sheet.getRow(rowNumber)
                        if (!row || (0..<11).every { cellAt(row, it) == null || cellTypeName(cellAt(row, it)) == 'BLANK' }) {
                            return null
                        }
                        [
                                productCode          : stringValue(cellAt(row, 0), evaluator),
                                productName          : stringValue(cellAt(row, 1), evaluator),
                                facility             : stringValue(cellAt(row, 2), evaluator),
                                status               : stringValue(cellAt(row, 3), evaluator),
                                internalLocation     : stringValue(cellAt(row, 4), evaluator),
                                preferredBinLocation : stringValue(cellAt(row, 5), evaluator),
                                replenishmentLocation: stringValue(cellAt(row, 6), evaluator),
                                abcClass             : stringValue(cellAt(row, 7), evaluator),
                                minQuantity          : numberValue(cellAt(row, 8), evaluator),
                                reorderQuantity      : numberValue(cellAt(row, 9), evaluator),
                                maxQuantity          : numberValue(cellAt(row, 10), evaluator),
                        ]
                    }
                    .findAll { it != null }
        } finally {
            workbook.close()
        }
    }

    private Object stringValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null
        }
        String value
        if (cellTypeName(cell) == 'FORMULA') {
            CellValue evaluated = evaluator.evaluate(cell)
            value = evaluated.cellTypeEnum == CellType.NUMERIC && evaluated.numberValue == evaluated.numberValue.intValue()
                    ? evaluated.numberValue.intValue().toString()
                    : evaluated.formatAsString()
        } else {
            value = cell.toString()
        }
        if (value.equalsIgnoreCase('true') || value.equalsIgnoreCase('false')) {
            return value.equalsIgnoreCase('true')
        }
        value.isEmpty() ? null : value
    }

    private Cell cellAt(def row, int columnIndex) {
        row.cellIterator().find { it.columnIndex == columnIndex }
    }

    private Number numberValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null
        }
        if (cellTypeName(cell) == 'NUMERIC') {
            return cell.numericCellValue
        }
        String value
        if (cellTypeName(cell) == 'FORMULA') {
            CellValue evaluated = evaluator.evaluate(cell)
            return evaluated.cellTypeEnum == CellType.NUMERIC ? evaluated.numberValue : null
        }
        value = cell.toString()
        value ==~ /-?\d+/ ? value.toInteger() : null
    }

    private String cellTypeName(Cell cell) {
        cell.getCellTypeEnum().name()
    }
}
