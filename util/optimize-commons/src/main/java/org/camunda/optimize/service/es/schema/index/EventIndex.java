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
      .startObject(EventDto.Fields.id)
        .field("type", "keyword")
      .endObject()
      .startObject(EventDto.Fields.eventName)
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
      .startObject(EventDto.Fields.traceId)
        .field("type", "keyword")
      .endObject()
      .startObject(EventDto.Fields.timestamp)
        .field("type", "date")
      .endObject()
      .startObject(EventDto.Fields.ingestionTimestamp)
        .field("type", "date")
      .endObject()
      .startObject(EventDto.Fields.duration)
        .field("type", "long")
      .endObject()
      .startObject(EventDto.Fields.group)
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
      .startObject(EventDto.Fields.source)
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
      .startObject(EventDto.Fields.data)
        .field("enabled", false)
      .endObject()
      ;
    // @formatter:on
  }

}
