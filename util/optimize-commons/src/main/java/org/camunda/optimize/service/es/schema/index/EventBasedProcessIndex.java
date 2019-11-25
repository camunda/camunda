/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.event.IndexableEventBasedProcessDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.MappedEventDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EventBasedProcessIndex extends StrictIndexMappingCreator {
  public static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.EVENT_BASED_PROCESS_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    XContentBuilder newXContentBuilder = xContentBuilder
      .startObject(IndexableEventBasedProcessDto.Fields.id)
        .field("type", "keyword")
      .endObject()
      .startObject(IndexableEventBasedProcessDto.Fields.name)
        .field("type", "keyword")
      .endObject()
      .startObject(IndexableEventBasedProcessDto.Fields.xml)
        .field("type", "text")
        .field("index", true)
        .field("analyzer", "is_present_analyzer")
      .endObject()
      .startObject(IndexableEventBasedProcessDto.Fields.mappings)
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
      .startObject(IndexableEventMappingDto.Fields.flowNodeId)
        .field("type", "keyword")
      .endObject()
      .startObject(IndexableEventMappingDto.Fields.start)
        .field("type", "object")
        .startObject("properties");
          addEventMappingFields(newXContentBuilder)
        .endObject()
      .endObject()
      .startObject(IndexableEventMappingDto.Fields.end)
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
      .startObject(MappedEventDto.Fields.group)
        .field("type", "keyword")
      .endObject()
      .startObject(MappedEventDto.Fields.source)
        .field("type", "keyword")
      .endObject()
      .startObject(MappedEventDto.Fields.eventName)
        .field("type", "keyword")
      .endObject();
    // @formatter:on
  }

}
