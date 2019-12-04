/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EventIndex extends StrictIndexMappingCreator {

  public static final String ID = EventDto.Fields.id;
  public static final String EVENT_NAME = EventDto.Fields.eventName;
  public static final String TRACE_ID = EventDto.Fields.traceId;
  public static final String TIMESTAMP = EventDto.Fields.timestamp;
  public static final String INGESTION_TIMESTAMP = EventDto.Fields.ingestionTimestamp;
  public static final String DURATION = EventDto.Fields.duration;
  public static final String GROUP = EventDto.Fields.group;
  public static final String SOURCE = EventDto.Fields.source;
  public static final String DATA = EventDto.Fields.data;

  public static final String N_GRAM_FIELD = "nGramField";
  public static final String LOWERCASE_FIELD = "lowercase";

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
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_NAME)
        .field("type", "keyword")
        .startObject("fields")
          .startObject(N_GRAM_FIELD)
            .field("type", "text")
            .field("analyzer", "lowercase_ngram")
          .endObject()
          .startObject(LOWERCASE_FIELD)
            .field("type", "keyword")
            .field("normalizer", "lowercase_normalizer")
          .endObject()
        .endObject()
      .endObject()
      .startObject(TRACE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(TIMESTAMP)
        .field("type", "date")
      .endObject()
      .startObject(INGESTION_TIMESTAMP)
        .field("type", "date")
      .endObject()
      .startObject(DURATION)
        .field("type", "long")
      .endObject()
      .startObject(GROUP)
        .field("type", "keyword")
        .startObject("fields")
          .startObject(N_GRAM_FIELD)
            .field("type", "text")
            .field("analyzer", "lowercase_ngram")
          .endObject()
          .startObject(LOWERCASE_FIELD)
            .field("type", "keyword")
            .field("normalizer", "lowercase_normalizer")
          .endObject()
        .endObject()
      .endObject()
      .startObject(SOURCE)
        .field("type", "keyword")
        .startObject("fields")
          .startObject(N_GRAM_FIELD)
            .field("type", "text")
            .field("analyzer", "lowercase_ngram")
          .endObject()
          .startObject(LOWERCASE_FIELD)
            .field("type", "keyword")
            .field("normalizer", "lowercase_normalizer")
          .endObject()
        .endObject()
      .endObject()
      .startObject(DATA)
        .field("enabled", false)
      .endObject()
      ;
    // @formatter:on
  }

}
