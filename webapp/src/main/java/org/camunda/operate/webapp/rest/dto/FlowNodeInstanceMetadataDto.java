/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventMetadataEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.util.ConversionUtils;

public class FlowNodeInstanceMetadataDto {

  private String id;

  /**
   * Workflow data.
   */
  private String workflowId;
  private String workflowInstanceId;
  private String bpmnProcessId;

  /**
   * Activity data.
   */
  private String flowNodeId;
  private String flowNodeInstanceId;

  /**
   * Event data.
   */
  private EventSourceType eventSourceType;
  private EventType eventType;
  private OffsetDateTime dateTime;

  private EventMetadataDto metadata;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public void setFlowNodeInstanceId(String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
  }

  public EventSourceType getEventSourceType() {
    return eventSourceType;
  }

  public void setEventSourceType(EventSourceType eventSourceType) {
    this.eventSourceType = eventSourceType;
  }

  public EventType getEventType() {
    return eventType;
  }

  public void setEventType(EventType eventType) {
    this.eventType = eventType;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  public void setDateTime(OffsetDateTime dateTime) {
    this.dateTime = dateTime;
  }

  public EventMetadataDto getMetadata() {
    return metadata;
  }

  public void setMetadata(EventMetadataDto metadata) {
    this.metadata = metadata;
  }

  public static FlowNodeInstanceMetadataDto createFrom(EventEntity eventEntity) {
    FlowNodeInstanceMetadataDto eventDto = new FlowNodeInstanceMetadataDto();
    eventDto.setId(eventEntity.getId());
    eventDto.setFlowNodeId(eventEntity.getFlowNodeId());
    eventDto.setFlowNodeInstanceId(ConversionUtils.toStringOrNull(eventEntity.getFlowNodeInstanceKey()));
    eventDto.setBpmnProcessId(eventEntity.getBpmnProcessId());
    eventDto.setDateTime(eventEntity.getDateTime());
    eventDto.setEventSourceType(eventEntity.getEventSourceType());
    eventDto.setEventType(eventEntity.getEventType());
    eventDto.setWorkflowId(ConversionUtils.toStringOrNull(eventEntity.getWorkflowKey()));
    eventDto.setWorkflowInstanceId(ConversionUtils.toStringOrNull(eventEntity.getWorkflowInstanceKey()));

    EventMetadataEntity eventMetadataEntity = eventEntity.getMetadata();
    if (eventMetadataEntity != null) {
      EventMetadataDto eventMetadataDto = EventMetadataDto.createFrom(eventMetadataEntity);
      eventDto.setMetadata(eventMetadataDto);
    }

    return eventDto;
  }

  public static List<FlowNodeInstanceMetadataDto> createFrom(List<EventEntity> eventEntities) {
    List<FlowNodeInstanceMetadataDto> result = new ArrayList<>();
    if (eventEntities != null) {
      for (EventEntity eventEntity: eventEntities) {
        if (eventEntity != null) {
          result.add(createFrom(eventEntity));
        }
      }
    }
    return result;
  }

}
