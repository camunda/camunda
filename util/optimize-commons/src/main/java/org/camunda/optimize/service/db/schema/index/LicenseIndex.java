/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index;

import org.camunda.optimize.service.db.LicenseDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public abstract class LicenseIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {
    public static final int VERSION = 3;

    public static final String LICENSE = LicenseDto.Fields.license;

    @Override
    public String getIndexName() {
        return DatabaseConstants.LICENSE_INDEX_NAME;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
        // @formatter:off
        return xContentBuilder
                .startObject(LICENSE)
                .field("type", "text")
                .field("index", false)
                .endObject();
        // @formatter:on
    }
}