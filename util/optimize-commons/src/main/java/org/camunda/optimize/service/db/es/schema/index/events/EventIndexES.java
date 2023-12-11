/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.schema.index.events;

import org.camunda.optimize.service.db.schema.index.events.EventIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.SORT_FIELD_SETTING;
import static org.camunda.optimize.service.db.DatabaseConstants.SORT_ORDER_SETTING;
import static org.camunda.optimize.service.db.DatabaseConstants.SORT_SETTING;

public class EventIndexES extends EventIndex<XContentBuilder> {

    @Override
    public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder) throws IOException {
        return contentBuilder.field(key, value);
    }

    @Override
    public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                             ConfigurationService configurationService) throws IOException {
        // @formatter:off
        final XContentBuilder newXContentBuilder = super.getStaticSettings(xContentBuilder, configurationService);
        return newXContentBuilder
                .startObject(SORT_SETTING)
                .field(SORT_FIELD_SETTING, INGESTION_TIMESTAMP)
                .field(SORT_ORDER_SETTING, "desc")
                .endObject();
        // @formatter:on
    }

}
