/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import java.time.OffsetDateTime;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class FlowNodeInstanceMetadataDto implements FlowNodeInstanceMetadata {
  private String flowNodeId;

  private String flowNodeInstanceId;
  private FlowNodeType flowNodeType;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private String eventId;

  private String messageName;
  private String correlationKey;

  public FlowNodeInstanceMetadataDto(
      final String flowNodeId,
      final String flowNodeInstanceId,
      final FlowNodeType flowNodeType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final EventEntity event) {
    this.flowNodeId = flowNodeId;
    this.flowNodeInstanceId = flowNodeInstanceId;
    this.flowNodeType = flowNodeType;
    this.startDate = startDate;
    this.endDate = endDate;
    eventId = event.getId();
    final var eventMetadata = event.getMetadata();
    if (eventMetadata != null) {
      messageName = eventMetadata.getMessageName();
      correlationKey = eventMetadata.getCorrelationKey();
    }
  }

  public FlowNodeInstanceMetadataDto() {}

  @Override
  public FlowNodeType getFlowNodeType() {
    return flowNodeType;
  }

  @Override
  public FlowNodeInstanceMetadataDto setFlowNodeType(final FlowNodeType flowNodeType) {
    this.flowNodeType = flowNodeType;
    return this;
  }

  @Override
  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  @Override
  public FlowNodeInstanceMetadataDto setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  @Override
  public String getFlowNodeId() {
    return flowNodeId;
  }

  @Override
  public FlowNodeInstanceMetadataDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  @Override
  public OffsetDateTime getStartDate() {
    return startDate;
  }

  @Override
  public FlowNodeInstanceMetadataDto setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  @Override
  public OffsetDateTime getEndDate() {
    return endDate;
  }

  @Override
  public FlowNodeInstanceMetadataDto setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  @Override
  public String getEventId() {
    return eventId;
  }

  @Override
  public FlowNodeInstanceMetadataDto setEventId(final String eventId) {
    this.eventId = eventId;
    return this;
  }

  @Override
  public String getMessageName() {
    return messageName;
  }

  @Override
  public FlowNodeInstanceMetadataDto setMessageName(final String messageName) {
    this.messageName = messageName;
    return this;
  }

  @Override
  public String getCorrelationKey() {
    return correlationKey;
  }

  @Override
  public FlowNodeInstanceMetadataDto setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        flowNodeId,
        flowNodeInstanceId,
        flowNodeType,
        startDate,
        endDate,
        eventId,
        messageName,
        correlationKey);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeInstanceMetadataDto that = (FlowNodeInstanceMetadataDto) o;
    return Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && flowNodeType == that.flowNodeType
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(eventId, that.eventId)
        && Objects.equals(messageName, that.messageName)
        && Objects.equals(correlationKey, that.correlationKey);
  }

  @Override
  public String toString() {
    return "FlowNodeInstanceMetadataDto{"
        + "flowNodeId='"
        + flowNodeId
        + '\''
        + ", flowNodeInstanceId='"
        + flowNodeInstanceId
        + '\''
        + ", flowNodeType="
        + flowNodeType
        + ", startDate="
        + startDate
        + ", endDate="
        + endDate
        + ", eventId='"
        + eventId
        + '\''
        + ", messageName='"
        + messageName
        + '\''
        + ", correlationKey='"
        + correlationKey
        + '\''
        + '}';
  }
}
