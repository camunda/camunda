/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version27;

import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventProcessPublishStateDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class EventProcessPublishStateIndexV1 extends StrictIndexMappingCreator {
  public static final int VERSION = 1;
  public static final String ID = IndexableEventProcessPublishStateDto.Fields.id;
  public static final String PROCESS_MAPPING_ID = IndexableEventProcessPublishStateDto.Fields.processMappingId;
  public static final String NAME = IndexableEventProcessPublishStateDto.Fields.name;
  public static final String PUBLISH_DATE_TIME = IndexableEventProcessPublishStateDto.Fields.publishDateTime;
  public static final String LAST_IMPORTED_EVENT_DATE_TIME = "lastImportedEventIngestDateTime";
  public static final String STATE = IndexableEventProcessPublishStateDto.Fields.state;
  public static final String PUBLISH_PROGRESS = IndexableEventProcessPublishStateDto.Fields.publishProgress;
  public static final String DELETED = IndexableEventProcessPublishStateDto.Fields.deleted;
  public static final String XML = IndexableEventProcessPublishStateDto.Fields.xml;
  public static final String MAPPINGS = IndexableEventProcessPublishStateDto.Fields.mappings;

  public static final String FLOWNODE_ID = IndexableEventMappingDto.Fields.flowNodeId;
  public static final String START = IndexableEventMappingDto.Fields.start;
  public static final String END = IndexableEventMappingDto.Fields.end;

  public static final String GROUP = EventTypeDto.Fields.group;
  public static final String SOURCE = EventTypeDto.Fields.source;
  public static final String EVENT_NAME = EventTypeDto.Fields.eventName;

  public static final String EVENT_SOURCE_ID = EventSourceEntryDto.Fields.id;
  public static final String EVENT_SOURCE_TYPE = EventSourceEntryDto.Fields.type;
  public static final String EVENT_SOURCE_EVENT_SCOPE = EventSourceEntryDto.Fields.eventScope;
  public static final String EVENT_SOURCE_PROC_DEF_KEY = EventSourceEntryDto.Fields.processDefinitionKey;
  public static final String EVENT_SOURCE_VERSIONS = EventSourceEntryDto.Fields.versions;
  public static final String EVENT_SOURCE_TENANTS = EventSourceEntryDto.Fields.tenants;
  public static final String EVENT_SOURCE_TRACED_BY_BUSINESS_KEY = EventSourceEntryDto.Fields.tracedByBusinessKey;
  public static final String EVENT_SOURCE_TRACE_VARIABLE = EventSourceEntryDto.Fields.traceVariable;

  @Override
  public String getIndexName() {
    return EVENT_PROCESS_PUBLISH_STATE_INDEX;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    final XContentBuilder newXContentBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_MAPPING_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(PUBLISH_DATE_TIME)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(LAST_IMPORTED_EVENT_DATE_TIME)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(STATE)
      .field("type", "keyword")
      .endObject()
      .startObject(PUBLISH_PROGRESS)
        .field("type", "double")
      .endObject()
      .startObject(DELETED)
        .field("type", "boolean")
      .endObject()
      .startObject(XML)
        .field("type", "text")
        .field("index", true)
        .field("analyzer", "is_present_analyzer")
      .endObject()
      .startObject(MAPPINGS)
        .field("type", "object")
        .startObject("properties");
          addMappingFields(newXContentBuilder)
        .endObject()
      .endObject()
      ;
    // @formatter:on
    return newXContentBuilder;
  }

  private XContentBuilder addMappingFields(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    XContentBuilder newXContentBuilder = xContentBuilder
      .startObject(FLOWNODE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(START)
        .field("type", "object")
      .startObject("properties");
        addEventMappingFields(newXContentBuilder)
        .endObject()
      .endObject()
      .startObject(END)
        .field("type", "object")
      .startObject("properties");
        addEventMappingFields(newXContentBuilder)
        .endObject()
      .endObject();
    // @formatter:on
    return newXContentBuilder;
  }

  private XContentBuilder addEventMappingFields(final XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      // @formatter:off
      .startObject(GROUP)
        .field("type", "keyword")
      .endObject()
      .startObject(SOURCE)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_NAME)
        .field("type", "keyword")
      .endObject();
    // @formatter:on
  }

  private XContentBuilder addEventSourcesField(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(EVENT_SOURCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_EVENT_SCOPE)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_PROC_DEF_KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_VERSIONS)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_TENANTS)
        .field("type", "keyword")
      .endObject()
      .startObject(EVENT_SOURCE_TRACED_BY_BUSINESS_KEY)
        .field("type", "boolean")
      .endObject()
      .startObject(EVENT_SOURCE_TRACE_VARIABLE)
        .field("type", "keyword")
      .endObject();
    // @formatter:on
  }
}
