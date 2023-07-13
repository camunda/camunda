/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.Objects;

public class EventEntity extends OperateZeebeEntity<EventEntity> {

  /**
   * Process data.
   */
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private String bpmnProcessId;

  /**
   * Activity data.
   */
  private String flowNodeId;
  private Long flowNodeInstanceKey;

  /**
   * Event data.
   */
  private EventSourceType eventSourceType;
  private EventType eventType;
  private OffsetDateTime dateTime;

  /**
   * Metadata
   */
  private EventMetadataEntity metadata;
  private String tenantId;

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public void setFlowNodeInstanceKey(Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
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

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  public void setDateTime(OffsetDateTime dateTime) {
    this.dateTime = dateTime;
  }

  public EventMetadataEntity getMetadata() {
    return metadata;
  }

  public void setMetadata(EventMetadataEntity metadata) {
    this.metadata = metadata;
  }

  public String getTenantId() {
    return tenantId;
  }

  public EventEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;
    EventEntity that = (EventEntity) o;
    return Objects.equals(processDefinitionKey, that.processDefinitionKey) && Objects.equals(processInstanceKey,
        that.processInstanceKey) && Objects.equals(bpmnProcessId, that.bpmnProcessId) && Objects.equals(flowNodeId,
        that.flowNodeId) && Objects.equals(flowNodeInstanceKey,
        that.flowNodeInstanceKey) && eventSourceType == that.eventSourceType && eventType == that.eventType && Objects.equals(
        dateTime, that.dateTime) && Objects.equals(metadata, that.metadata) && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), processDefinitionKey, processInstanceKey, bpmnProcessId, flowNodeId,
        flowNodeInstanceKey, eventSourceType, eventType, dateTime, metadata, tenantId);
  }
}
