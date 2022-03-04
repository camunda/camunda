/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.events;

import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FIELDS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LOWERCASE_NGRAM;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LOWERCASE_NORMALIZER;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_FIELD_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_ORDER_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_SETTING;

public class EventIndex extends DefaultIndexMappingCreator {

  public static final String ID = EventDto.Fields.id;
  public static final String EVENT_NAME = EventDto.Fields.eventName;
  public static final String TRACE_ID = EventDto.Fields.traceId;
  public static final String TIMESTAMP = EventDto.Fields.timestamp;
  public static final String INGESTION_TIMESTAMP = EventDto.Fields.ingestionTimestamp;
  public static final String GROUP = EventDto.Fields.group;
  public static final String SOURCE = EventDto.Fields.source;
  public static final String DATA = EventDto.Fields.data;
  public static final String N_GRAM_FIELD = "nGramField";

  public static final int VERSION = 4;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_NAME;
  }

  @Override
  public String getIndexNameInitialSuffix() {
    return ElasticsearchConstants.INDEX_SUFFIX_PRE_ROLLOVER;
  }

  @Override
  public boolean isCreateFromTemplate() {
    return true;
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
      .startObject(TRACE_ID)
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
      .startObject(TIMESTAMP)
        .field("type", "date")
      .endObject()
      .startObject(INGESTION_TIMESTAMP)
        .field("type", "date")
      .endObject()
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
      .startObject(DATA)
        .field(MAPPING_ENABLED_SETTING, false)
      .endObject()
      ;
    // @formatter:on
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
