package org.pih.warehouse.data

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class DataExportQueryValidatorSpec extends Specification {

    void "isReadOnlyQuery accepts '#query'"() {
        expect:
        DataExportQueryValidator.isReadOnlyQuery(query)

        where:
        query << [
                "SELECT * FROM product",
                "select p.name, p.product_code from product p where p.active = 1 order by p.name",
                "  SELECT count(*) FROM inventory_item ;  ",
                "WITH latest AS (SELECT id FROM shipment) SELECT * FROM latest",
                "SELECT date_created, last_updated FROM person",
        ]
    }

    void "isReadOnlyQuery rejects '#query'"() {
        expect:
        !DataExportQueryValidator.isReadOnlyQuery(query)

        where:
        query << [
                null,
                "",
                "   ",
                ";",
                "UPDATE person SET password_hash = 'x'",
                "DELETE FROM person",
                "DROP TABLE person",
                "INSERT INTO role (name) VALUES ('x')",
                "GRANT ALL ON *.* TO 'attacker'@'%'",
                "SELECT * FROM person; DROP TABLE person",
                "SELECT * FROM person -- ; DROP TABLE person",
                "SELECT * FROM person /* comment */",
                "SELECT * FROM person # comment",
                "SELECT * INTO OUTFILE '/tmp/person.csv' FROM person",
                "SELECT load_file('/etc/passwd')",
                "SELECT @x := password_hash FROM person",
                "CALL some_procedure()",
                "call some_procedure()",
        ]
    }
}
