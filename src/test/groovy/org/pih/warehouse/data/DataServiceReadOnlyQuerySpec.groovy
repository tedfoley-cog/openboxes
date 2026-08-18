package org.pih.warehouse.data

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class DataServiceReadOnlyQuerySpec extends Specification {

    void "validateReadOnlyQuery should accept '#query'"() {
        expect:
        DataService.validateReadOnlyQuery(query) == expected

        where:
        query                                | expected
        "select * from product"              | "select * from product"
        "  SELECT * from product ;  "        | "SELECT * from product"
        "with p as (select 1) select * from p" | "with p as (select 1) select * from p"
    }

    void "validateReadOnlyQuery should reject '#query'"() {
        when:
        DataService.validateReadOnlyQuery(query)

        then:
        thrown(IllegalArgumentException)

        where:
        query << [
            null,
            "",
            "   ",
            "delete from product",
            "update user set password = 'x'",
            "drop table product",
            "grant all on *.* to 'attacker'@'%'",
            "select 1; drop table product",
            "select * from user into outfile '/tmp/pwn'",
            "select * from user into outfile '/tmp/pwn'; select 1",
        ]
    }
}
