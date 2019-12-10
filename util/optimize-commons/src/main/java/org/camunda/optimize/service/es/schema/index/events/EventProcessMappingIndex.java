/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.events;

import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventProcessMappingDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Component
public class EventProcessMappingIndex extends StrictIndexMappingCreator {

  public static final int VERSION = 1;

  public static final String ID = IndexableEventProcessMappingDto.Fields.id;
  public static final String NAME = IndexableEventProcessMappingDto.Fields.name;
  public static final String XML = IndexableEventProcessMappingDto.Fields.xml;
  public static final String LAST_MODIFIED = IndexableEventProcessMappingDto.Fields.lastModified;
  public static final String LAST_MODIFIER = IndexableEventProcessMappingDto.Fields.lastModifier;
  public static final String MAPPINGS = IndexableEventProcessMappingDto.Fields.mappings;

  public static final String FLOWNODE_ID = IndexableEventMappingDto.Fields.flowNodeId;
  public static final String START = IndexableEventMappingDto.Fields.start;
  public static final String END = IndexableEventMappingDto.Fields.end;

  public static final String GROUP = EventTypeDto.Fields.group;
  public static final String SOURCE = EventTypeDto.Fields.source;
  public static final String EVENT_NAME = EventTypeDto.Fields.eventName;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    XContentBuilder newXContentBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIER)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIED)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(XML)
        .field("type", "text")
        .field("index", true)
        .field("analyzer", "is_present_analyzer")
      .endObject()
      .startObject(MAPPINGS)
        .field("type", "object")
        .startObject("properties");
          addNestedFlowNodeMappingsFields(newXContentBuilder)
        .endObject()
      .endObject();
    // @formatter:on
    return newXContentBuilder;
  }

  private XContentBuilder addNestedFlowNodeMappingsFields(XContentBuilder xContentBuilder) throws IOException {
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

  private XContentBuilder addEventMappingFields(XContentBuilder xContentBuilder) throws IOException {
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

}
