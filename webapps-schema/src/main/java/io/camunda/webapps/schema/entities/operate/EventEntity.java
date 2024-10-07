/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate;

import java.time.OffsetDateTime;
import java.util.Objects;

public class EventEntity extends OperateZeebeEntity<EventEntity> {

  /** Process data. */
  private Long processDefinitionKey;

  private Long processInstanceKey;
  private String bpmnProcessId;

  /** Activity data. */
  private String flowNodeId;

  private Long flowNodeInstanceKey;

  /** Event data. */
  private EventSourceType eventSourceType;

  private EventType eventType;
  private OffsetDateTime dateTime;

  /** Metadata */
  private EventMetadataEntity metadata;

  private String tenantId = DEFAULT_TENANT_ID;

  private Long position;
  private Long positionIncident;
  private Long positionProcessMessageSubscription;
  private Long positionJob;

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public EventEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public EventEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public EventEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public EventEntity setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public EventEntity setFlowNodeInstanceKey(final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    return this;
  }

  public EventSourceType getEventSourceType() {
    return eventSourceType;
  }

  public EventEntity setEventSourceType(final EventSourceType eventSourceType) {
    this.eventSourceType = eventSourceType;
    return this;
  }

  public EventType getEventType() {
    return eventType;
  }

  public EventEntity setEventType(final EventType eventType) {
    this.eventType = eventType;
    return this;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  public EventEntity setDateTime(final OffsetDateTime dateTime) {
    this.dateTime = dateTime;
    return this;
  }

  public EventMetadataEntity getMetadata() {
    return metadata;
  }

  public EventEntity setMetadata(final EventMetadataEntity metadata) {
    this.metadata = metadata;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public EventEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public EventEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public Long getPositionIncident() {
    return positionIncident;
  }

  public EventEntity setPositionIncident(final Long positionIncident) {
    this.positionIncident = positionIncident;
    return this;
  }

  public Long getPositionProcessMessageSubscription() {
    return positionProcessMessageSubscription;
  }

  public EventEntity setPositionProcessMessageSubscription(
      final Long positionProcessMessageSubscription) {
    this.positionProcessMessageSubscription = positionProcessMessageSubscription;
    return this;
  }

  public Long getPositionJob() {
    return positionJob;
  }

  public EventEntity setPositionJob(final Long positionJob) {
    this.positionJob = positionJob;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        processDefinitionKey,
        processInstanceKey,
        bpmnProcessId,
        flowNodeId,
        flowNodeInstanceKey,
        eventSourceType,
        eventType,
        dateTime,
        metadata,
        tenantId,
        position,
        positionIncident,
        positionProcessMessageSubscription,
        positionJob);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final EventEntity that = (EventEntity) o;
    return Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceKey, that.flowNodeInstanceKey)
        && eventSourceType == that.eventSourceType
        && eventType == that.eventType
        && Objects.equals(dateTime, that.dateTime)
        && Objects.equals(metadata, that.metadata)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(position, that.position)
        && Objects.equals(positionIncident, that.positionIncident)
        && Objects.equals(
            positionProcessMessageSubscription, that.positionProcessMessageSubscription)
        && Objects.equals(positionJob, that.positionJob);
  }
}
