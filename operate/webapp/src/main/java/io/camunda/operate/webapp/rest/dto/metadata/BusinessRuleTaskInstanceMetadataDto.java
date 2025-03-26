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
public class BusinessRuleTaskInstanceMetadataDto extends JobFlowNodeInstanceMetadataDto
    implements FlowNodeInstanceMetadata {

  private String calledDecisionInstanceId;
  private String calledDecisionDefinitionName;

  public BusinessRuleTaskInstanceMetadataDto(
      final String flowNodeId,
      final String flowNodeInstanceId,
      final FlowNodeType flowNodeType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final EventEntity event,
      final String calledDecisionInstanceId,
      final String calledDecisionDefinitionName) {
    super(flowNodeId, flowNodeInstanceId, flowNodeType, startDate, endDate, event);
    if (calledDecisionInstanceId != null) {
      setCalledDecisionInstanceId(calledDecisionInstanceId);
    }
    if (calledDecisionDefinitionName != null) {
      setCalledDecisionDefinitionName(calledDecisionDefinitionName);
    }
  }

  public BusinessRuleTaskInstanceMetadataDto() {
    super();
  }

  public String getCalledDecisionInstanceId() {
    return calledDecisionInstanceId;
  }

  public BusinessRuleTaskInstanceMetadataDto setCalledDecisionInstanceId(
      final String calledDecisionInstanceId) {
    this.calledDecisionInstanceId = calledDecisionInstanceId;
    return this;
  }

  public String getCalledDecisionDefinitionName() {
    return calledDecisionDefinitionName;
  }

  public BusinessRuleTaskInstanceMetadataDto setCalledDecisionDefinitionName(
      final String calledDecisionDefinitionName) {
    this.calledDecisionDefinitionName = calledDecisionDefinitionName;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), calledDecisionInstanceId, calledDecisionDefinitionName);
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
    final BusinessRuleTaskInstanceMetadataDto that = (BusinessRuleTaskInstanceMetadataDto) o;
    return Objects.equals(calledDecisionInstanceId, that.calledDecisionInstanceId)
        && Objects.equals(calledDecisionDefinitionName, that.calledDecisionDefinitionName);
  }

  @Override
  public String toString() {
    return "BusinessRuleTaskInstanceMetadataDto{"
        + "calledDecisionInstanceId='"
        + calledDecisionInstanceId
        + '\''
        + ", calledDecisionDefinitionName='"
        + calledDecisionDefinitionName
        + '\''
        + "} "
        + super.toString();
  }
}
