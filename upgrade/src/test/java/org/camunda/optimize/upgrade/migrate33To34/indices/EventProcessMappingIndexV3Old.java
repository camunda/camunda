/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34.indices;

import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessMappingDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.migrate33To34.dto.EventSourceEntryDtoOld;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class EventProcessMappingIndexV3Old extends DefaultIndexMappingCreator {

  public static final int VERSION = 3;

  public static final String ID = EsEventProcessMappingDto.Fields.id;
  public static final String NAME = EsEventProcessMappingDto.Fields.name;
  public static final String XML = EsEventProcessMappingDto.Fields.xml;
  public static final String LAST_MODIFIED = EsEventProcessMappingDto.Fields.lastModified;
  public static final String LAST_MODIFIER = EsEventProcessMappingDto.Fields.lastModifier;
  public static final String MAPPINGS = EsEventProcessMappingDto.Fields.mappings;
  public static final String EVENT_SOURCES = EsEventProcessMappingDto.Fields.eventSources;

  public static final String FLOWNODE_ID = EsEventMappingDto.Fields.flowNodeId;
  public static final String START = EsEventMappingDto.Fields.start;
  public static final String END = EsEventMappingDto.Fields.end;

  public static final String GROUP = EventTypeDto.Fields.group;
  public static final String SOURCE = EventTypeDto.Fields.source;
  public static final String EVENT_NAME = EventTypeDto.Fields.eventName;
  public static final String EVENT_LABEL = EventTypeDto.Fields.eventLabel;

  public static final String EVENT_SOURCE_ID = EventSourceEntryDtoOld.Fields.id;
  public static final String EVENT_SOURCE_TYPE = EventSourceEntryDtoOld.Fields.type;
  public static final String EVENT_SOURCE_PROC_DEF_KEY = EventSourceEntryDtoOld.Fields.processDefinitionKey;
  public static final String EVENT_SOURCE_VERSIONS = EventSourceEntryDtoOld.Fields.versions;
  public static final String EVENT_SOURCE_TENANTS = EventSourceEntryDtoOld.Fields.tenants;
  public static final String EVENT_SOURCE_TRACED_BY_BUSINESS_KEY = EventSourceEntryDtoOld.Fields.tracedByBusinessKey;
  public static final String EVENT_SOURCE_TRACE_VARIABLE = EventSourceEntryDtoOld.Fields.traceVariable;
  public static final String EVENT_SOURCE_EVENT_SCOPE = EventSourceEntryDtoOld.Fields.eventScope;

  public static final String ROLES = EsEventProcessMappingDto.Fields.roles;
  public static final String ROLE_ID = EventProcessRoleRequestDto.Fields.id;
  public static final String ROLE_IDENTITY = EventProcessRoleRequestDto.Fields.identity;
  public static final String ROLE_IDENTITY_ID = IdentityDto.Fields.id;
  public static final String ROLE_IDENTITY_TYPE = IdentityDto.Fields.type;

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
      .endObject()
      .startObject(EVENT_SOURCES)
        .field("type", "nested");
         addEventSourcesField(newXContentBuilder)
      .endObject()
      .startObject(ROLES)
        .field("type", "object")
        .startObject("properties");
          addNestedRolesFields(newXContentBuilder)
        .endObject()
      .endObject();
    // @formatter:on
    return newXContentBuilder;
  }

  private XContentBuilder addNestedFlowNodeMappingsFields(final XContentBuilder xContentBuilder) throws IOException {
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
      .endObject()
      .startObject(EVENT_LABEL)
      .field("type", "keyword")
      .endObject();
    // @formatter:on
  }

  private XContentBuilder addEventSourcesField(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject("properties")
        .startObject(EVENT_SOURCE_ID)
          .field("type", "keyword")
        .endObject()
        .startObject(EVENT_SOURCE_TYPE)
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
        .endObject()
        .startObject(EVENT_SOURCE_EVENT_SCOPE)
          .field("type", "keyword")
        .endObject()
      .endObject();
    // @formatter:on
  }

  private XContentBuilder addNestedRolesFields(final XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      // @formatter:off
      .startObject(ROLE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ROLE_IDENTITY)
        .field("type", "object")
        .startObject("properties")
          .startObject(ROLE_IDENTITY_ID)
            .field("type", "keyword")
          .endObject()
          .startObject(ROLE_IDENTITY_TYPE)
            .field("type", "keyword")
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }

}
