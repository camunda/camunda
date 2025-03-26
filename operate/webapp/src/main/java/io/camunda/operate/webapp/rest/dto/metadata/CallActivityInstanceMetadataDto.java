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
public class CallActivityInstanceMetadataDto extends JobFlowNodeInstanceMetadataDto
    implements FlowNodeInstanceMetadata {

  private String calledProcessInstanceId;
  private String calledProcessDefinitionName;

  public CallActivityInstanceMetadataDto(
      final String flowNodeId,
      final String flowNodeInstanceId,
      final FlowNodeType flowNodeType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final EventEntity event,
      final String calledProcessInstanceId,
      final String calledProcessDefinitionName) {
    super(flowNodeId, flowNodeInstanceId, flowNodeType, startDate, endDate, event);
    setCalledProcessInstanceId(calledProcessInstanceId);
    setCalledProcessDefinitionName(calledProcessDefinitionName);
  }

  public CallActivityInstanceMetadataDto() {
    super();
  }

  public String getCalledProcessInstanceId() {
    return calledProcessInstanceId;
  }

  public CallActivityInstanceMetadataDto setCalledProcessInstanceId(
      final String calledProcessInstanceId) {
    this.calledProcessInstanceId = calledProcessInstanceId;
    return this;
  }

  public String getCalledProcessDefinitionName() {
    return calledProcessDefinitionName;
  }

  public CallActivityInstanceMetadataDto setCalledProcessDefinitionName(
      final String calledProcessDefinitionName) {
    this.calledProcessDefinitionName = calledProcessDefinitionName;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), calledProcessInstanceId, calledProcessDefinitionName);
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
    final CallActivityInstanceMetadataDto that = (CallActivityInstanceMetadataDto) o;
    return Objects.equals(calledProcessInstanceId, that.calledProcessInstanceId)
        && Objects.equals(calledProcessDefinitionName, that.calledProcessDefinitionName);
  }

  @Override
  public String toString() {
    return "CallActivityInstanceMetadataDto{"
        + "calledProcessInstanceId='"
        + calledProcessInstanceId
        + '\''
        + ", calledProcessDefinitionName='"
        + calledProcessDefinitionName
        + '\''
        + "} "
        + super.toString();
  }
}
