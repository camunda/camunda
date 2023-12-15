/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index.events;

import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.FIELDS;
import static org.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NGRAM;
import static org.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NORMALIZER;

public abstract class EventSequenceCountIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final String ID = EventSequenceCountDto.Fields.id;
  public static final String SOURCE_EVENT = EventSequenceCountDto.Fields.sourceEvent;
  public static final String TARGET_EVENT = EventSequenceCountDto.Fields.targetEvent;
  public static final String COUNT = EventSequenceCountDto.Fields.count;

  public static final String GROUP = EventTypeDto.Fields.group;
  public static final String SOURCE = EventTypeDto.Fields.source;
  public static final String EVENT_NAME = EventTypeDto.Fields.eventName;
  public static final String EVENT_LABEL = EventTypeDto.Fields.eventLabel;

  public static final String N_GRAM_FIELD = "nGramField";

  public static final int VERSION = 4;

  private final String indexName;

  protected EventSequenceCountIndex(final String indexKey) {
    this.indexName = constructIndexName(indexKey);
  }

  public static String constructIndexName(final String indexKey) {
    return DatabaseConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX + indexKey.toLowerCase();
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
          .startObject(EVENT_LABEL)
            .field("type", "keyword")
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
          .startObject(EVENT_LABEL)
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
