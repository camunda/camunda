/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.events;

import org.camunda.optimize.dto.optimize.persistence.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.FlowNodeInstanceUpdateDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class EventProcessInstanceIndex extends ProcessInstanceIndex {
  public static final String PENDING_FLOW_NODE_UPDATES = EventProcessInstanceDto.Fields.pendingFlowNodeInstanceUpdates;
  public static final String ACTIVITY_UPDATE_ID = "id";
  public static final String ACTIVITY_UPDATE_SOURCE_EVENT_ID = FlowNodeInstanceUpdateDto.Fields.sourceEventId;
  public static final String ACTIVITY_UPDATE_ACTIVITY_ID = FlowNodeInstanceUpdateDto.Fields.flowNodeId;
  public static final String ACTIVITY_UPDATE_ACTIVITY_TYPE = FlowNodeInstanceUpdateDto.Fields.flowNodeType;
  public static final String ACTIVITY_UPDATE_START_DATE = FlowNodeInstanceUpdateDto.Fields.startDate;
  public static final String ACTIVITY_UPDATE_END_DATE = FlowNodeInstanceUpdateDto.Fields.endDate;

  public EventProcessInstanceIndex(final String eventProcessId) {
    super(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + eventProcessId);
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
      .startObject(ACTIVITY_UPDATE_START_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
        .field("index", "false")
      .endObject()
      .startObject(ACTIVITY_UPDATE_END_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
        .field("index", "false")
      .endObject();
    // @formatter:on
  }
}
