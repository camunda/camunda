/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.events;

import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.dto.optimize.query.event.TracedEventDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EventTraceStateIndex extends StrictIndexMappingCreator {

  public static final int VERSION = 1;

  public static final String TRACE_ID = EventTraceStateDto.Fields.traceId;
  public static final String EVENT_TRACE = EventTraceStateDto.Fields.eventTrace;

  public static final String GROUP = TracedEventDto.Fields.group;
  public static final String SOURCE = TracedEventDto.Fields.source;
  public static final String EVENT_NAME = TracedEventDto.Fields.eventName;
  public static final String TIMESTAMP = TracedEventDto.Fields.timestamp;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(TRACE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_TRACE)
        .field("type", "object")
        .startObject("properties")
          .startObject(GROUP)
            .field("type", "keyword")
          .endObject()
          .startObject(SOURCE)
            .field("type", "keyword")
          .endObject()
          .startObject(EVENT_NAME)
            .field("type", "keyword")
          .endObject()
          .startObject(TIMESTAMP)
            .field("type", "date")
          .endObject()
        .endObject()
      .endObject()
      ;
    // @formatter:on
  }

}
