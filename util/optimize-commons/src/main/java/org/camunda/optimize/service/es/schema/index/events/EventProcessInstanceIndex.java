/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.events;

import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceUpdateDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class EventProcessInstanceIndex extends ProcessInstanceIndex {

  public static final String PENDING_FLOW_NODE_UPDATES = EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates;
  public static final String ACTIVITY_UPDATE_ID = "id";
  public static final String ACTIVITY_UPDATE_SOURCE_EVENT_ID = FlowNodeInstanceUpdateDto.Fields.sourceEventId;
  public static final String ACTIVITY_UPDATE_ACTIVITY_ID = FlowNodeInstanceUpdateDto.Fields.flowNodeId;
  public static final String ACTIVITY_UPDATE_ACTIVITY_TYPE = FlowNodeInstanceUpdateDto.Fields.flowNodeType;
  public static final String ACTIVITY_UPDATE_MAPPED_AS = FlowNodeInstanceUpdateDto.Fields.mappedAs;
  public static final String ACTIVITY_UPDATE_DATE = FlowNodeInstanceUpdateDto.Fields.date;

  public static final String CORRELATED_EVENTS_BY_EVENT_ID = EventProcessInstanceDto.Fields.correlatedEventsById;

  public EventProcessInstanceIndex(final String eventProcessId) {
    super(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + eventProcessId.toLowerCase());
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    // @formatter:off
    XContentBuilder newBuilder =  super.addProperties(builder)
      .startObject(PENDING_FLOW_NODE_UPDATES)
        .field("type", "object")
        .startObject("properties");
          addPendingEventUpdateObjectFields(newBuilder)
        .endObject()
      .endObject()
      .startObject(CORRELATED_EVENTS_BY_EVENT_ID)
        .field("type", "object")
        .field(MAPPING_ENABLED_SETTING, "false")
      .endObject();
    return newBuilder;
    // @formatter:on
  }

  private XContentBuilder addPendingEventUpdateObjectFields(final XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject(ACTIVITY_UPDATE_ID)
        .field("type", "keyword")
        .field("index", "false")
      .endObject()
      .startObject(ACTIVITY_UPDATE_SOURCE_EVENT_ID)
        .field("type", "keyword")
        .field("index", "false")
      .endObject()
      .startObject(ACTIVITY_UPDATE_ACTIVITY_ID)
        .field("type", "keyword")
        .field("index", "false")
      .endObject()
      .startObject(ACTIVITY_UPDATE_ACTIVITY_TYPE)
        .field("type", "keyword")
        .field("index", "false")
      .endObject()
      .startObject(ACTIVITY_UPDATE_MAPPED_AS)
        .field("type", "keyword")
        .field("index", "false")
      .endObject()
      .startObject(ACTIVITY_UPDATE_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
        .field("index", "false")
      .endObject();
    // @formatter:on
  }

}
