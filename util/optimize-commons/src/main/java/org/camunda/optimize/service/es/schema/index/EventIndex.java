/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EventIndex extends StrictIndexMappingCreator {
  public static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.EVENT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(EventDto.Fields.id.name())
        .field("type", "keyword")
      .endObject()
      .startObject(EventDto.Fields.eventName.name())
        .field("type", "keyword")
      .endObject()
      .startObject(EventDto.Fields.traceId.name())
        .field("type", "keyword")
      .endObject()
      .startObject(EventDto.Fields.timestamp.name())
        .field("type", "date")
      .endObject()
      .startObject(EventDto.Fields.duration.name())
        .field("type", "long")
      .endObject()
            .startObject(EventDto.Fields.group.name())
        .field("type", "keyword")
      .endObject()
      .startObject(EventDto.Fields.source.name())
        .field("type", "keyword")
      .endObject()
      .startObject(EventDto.Fields.data.name())
        .field("enabled", false)
      .endObject()
      ;
    // @formatter:on
  }

}
