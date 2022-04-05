/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import java.time.OffsetDateTime;

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

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    EventEntity that = (EventEntity) o;

    if (processDefinitionKey != null ? !processDefinitionKey.equals(that.processDefinitionKey) : that.processDefinitionKey != null)
      return false;
    if (processInstanceKey != null ? !processInstanceKey.equals(that.processInstanceKey) : that.processInstanceKey != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (flowNodeId != null ? !flowNodeId.equals(that.flowNodeId) : that.flowNodeId != null)
      return false;
    if (flowNodeInstanceKey != null ? !flowNodeInstanceKey.equals(that.flowNodeInstanceKey) : that.flowNodeInstanceKey != null)
      return false;
    if (eventSourceType != that.eventSourceType)
      return false;
    if (eventType != that.eventType)
      return false;
    if (dateTime != null ? !dateTime.equals(that.dateTime) : that.dateTime != null)
      return false;
    return metadata != null ? metadata.equals(that.metadata) : that.metadata == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (processDefinitionKey != null ? processDefinitionKey.hashCode() : 0);
    result = 31 * result + (processInstanceKey != null ? processInstanceKey.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (flowNodeId != null ? flowNodeId.hashCode() : 0);
    result = 31 * result + (flowNodeInstanceKey != null ? flowNodeInstanceKey.hashCode() : 0);
    result = 31 * result + (eventSourceType != null ? eventSourceType.hashCode() : 0);
    result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
    result = 31 * result + (dateTime != null ? dateTime.hashCode() : 0);
    result = 31 * result + (metadata != null ? metadata.hashCode() : 0);
    return result;
  }
}
