/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.events;

import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FIELDS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LOWERCASE_NGRAM;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LOWERCASE_NORMALIZER;

public class EventSequenceCountIndex extends DefaultIndexMappingCreator {

  public static final String ID = EventSequenceCountDto.Fields.id;
  public static final String SOURCE_EVENT = EventSequenceCountDto.Fields.sourceEvent;
  public static final String TARGET_EVENT = EventSequenceCountDto.Fields.targetEvent;
  public static final String COUNT = EventSequenceCountDto.Fields.count;

  public static final String GROUP = EventTypeDto.Fields.group;
  public static final String SOURCE = EventTypeDto.Fields.source;
  public static final String EVENT_NAME = EventTypeDto.Fields.eventName;

  public static final String N_GRAM_FIELD = "nGramField";

  public static final int VERSION = 3;

  private final String indexName;

  public EventSequenceCountIndex(final String indexKey) {
    this.indexName = ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX + indexKey.toLowerCase();
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
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(SOURCE_EVENT)
        .field("type", "object")
        .startObject("properties")
          .startObject(GROUP)
            .field("type", "keyword")
            .startObject(FIELDS)
              .startObject(N_GRAM_FIELD)
                .field("type", "text")
                .field(ANALYZER, LOWERCASE_NGRAM)
              .endObject()
              .startObject(LOWERCASE)
                .field("type", "keyword")
                .field(NORMALIZER, LOWERCASE_NORMALIZER)
              .endObject()
            .endObject()
          .endObject()
          .startObject(SOURCE)
            .field("type", "keyword")
            .startObject(FIELDS)
              .startObject(N_GRAM_FIELD)
                .field("type", "text")
                .field(ANALYZER, LOWERCASE_NGRAM)
              .endObject()
              .startObject(LOWERCASE)
                .field("type", "keyword")
                .field(NORMALIZER, LOWERCASE_NORMALIZER)
              .endObject()
            .endObject()
          .endObject()
          .startObject(EVENT_NAME)
            .field("type", "keyword")
            .startObject(FIELDS)
              .startObject(N_GRAM_FIELD)
                .field("type", "text")
                .field(ANALYZER, LOWERCASE_NGRAM)
              .endObject()
              .startObject(LOWERCASE)
                .field("type", "keyword")
                .field(NORMALIZER, LOWERCASE_NORMALIZER)
              .endObject()
            .endObject()
          .endObject()
        .endObject()
      .endObject()
      .startObject(TARGET_EVENT)
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
        .endObject()
      .endObject()
      .startObject(COUNT)
        .field("type", "long")
      .endObject()
      ;
    // @formatter:on
  }

}
