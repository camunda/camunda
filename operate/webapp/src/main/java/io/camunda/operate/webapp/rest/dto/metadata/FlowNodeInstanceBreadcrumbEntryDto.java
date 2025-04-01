/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.metadata;

import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import java.util.Objects;

public class FlowNodeInstanceBreadcrumbEntryDto {

  private String flowNodeId;
  private FlowNodeType flowNodeType;

  public FlowNodeInstanceBreadcrumbEntryDto() {}

  public FlowNodeInstanceBreadcrumbEntryDto(
      final String flowNodeId, final FlowNodeType flowNodeType) {
    this.flowNodeId = flowNodeId;
    this.flowNodeType = flowNodeType;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public FlowNodeInstanceBreadcrumbEntryDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public FlowNodeType getFlowNodeType() {
    return flowNodeType;
  }

  public FlowNodeInstanceBreadcrumbEntryDto setFlowNodeType(final FlowNodeType flowNodeType) {
    this.flowNodeType = flowNodeType;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(flowNodeId, flowNodeType);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeInstanceBreadcrumbEntryDto that = (FlowNodeInstanceBreadcrumbEntryDto) o;
    return Objects.equals(flowNodeId, that.flowNodeId) && flowNodeType == that.flowNodeType;
  }

  @Override
  public String toString() {
    return "FlowNodeInstanceBreadcrumbEntryDto{"
        + "flowNodeId='"
        + flowNodeId
        + '\''
        + ", flowNodeType="
        + flowNodeType
        + '}';
  }
}
