/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.schema.index.events;

import org.camunda.optimize.service.db.schema.index.events.EventProcessMappingIndex;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public class EventProcessMappingIndexES extends EventProcessMappingIndex<XContentBuilder> {

    @Override
    public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder) throws IOException {
        return contentBuilder.field(key, value);
    }
}
