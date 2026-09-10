package org.pih.warehouse.common.domain.builder.core

import groovy.transform.InheritConstructors

import org.pih.warehouse.common.domain.builder.base.TestBuilder
import org.pih.warehouse.core.Tag

@InheritConstructors
class TagTestBuilder extends TestBuilder<Tag> {

    @Override
    protected Map<String, Object> getDefaults() {
        return [
                tag     : randomUtil.randomStringFieldValue("Tag"),
                isActive: true,
        ] as Map<String, Object>
    }

    TagTestBuilder tag(String tag) {
        args.tag = tag
        return this
    }

    TagTestBuilder isActive(Boolean isActive) {
        args.isActive = isActive
        return this
    }
}
