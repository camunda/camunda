/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index.events;

import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.TracedEventDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public abstract class EventTraceStateIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final String TRACE_ID = EventTraceStateDto.Fields.traceId;
  public static final String EVENT_TRACE = EventTraceStateDto.Fields.eventTrace;

  public static final String EVENT_ID = TracedEventDto.Fields.eventId;
  public static final String GROUP = TracedEventDto.Fields.group;
  public static final String SOURCE = TracedEventDto.Fields.source;
  public static final String EVENT_NAME = TracedEventDto.Fields.eventName;
  public static final String TIMESTAMP = TracedEventDto.Fields.timestamp;
  public static final String ORDER_COUNTER = TracedEventDto.Fields.orderCounter;

  public static final int VERSION = 2;
  private final String indexName;

  protected EventTraceStateIndex(final String indexKey) {
    this.indexName = constructIndexName(indexKey);
  }

  public static String constructIndexName(final String indexKey) {
    return DatabaseConstants.EVENT_TRACE_STATE_INDEX_PREFIX + indexKey.toLowerCase();
  }

  @Override
  public String getIndexName() {
    return indexName;
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
        .field("type", "nested")
        .startObject("properties")
          .startObject(EVENT_ID)
            .field("type", "keyword")
          .endObject()
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
          .startObject(ORDER_COUNTER)
            .field("type", "keyword")
          .endObject()
        .endObject()
      .endObject()
      ;
    // @formatter:on
  }

}
