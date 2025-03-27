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

public class FlowNodeMetadataRequestDto {

  private String flowNodeId;
  private String flowNodeInstanceId;
  private FlowNodeType flowNodeType;

  public FlowNodeMetadataRequestDto() {}

  public FlowNodeMetadataRequestDto(
      final String flowNodeId, final String flowNodeInstanceId, final FlowNodeType flowNodeType) {
    this.flowNodeId = flowNodeId;
    this.flowNodeInstanceId = flowNodeInstanceId;
    this.flowNodeType = flowNodeType;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public FlowNodeMetadataRequestDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public FlowNodeMetadataRequestDto setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public FlowNodeType getFlowNodeType() {
    return flowNodeType;
  }

  public FlowNodeMetadataRequestDto setFlowNodeType(final FlowNodeType flowNodeType) {
    this.flowNodeType = flowNodeType;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(flowNodeId, flowNodeInstanceId, flowNodeType);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeMetadataRequestDto that = (FlowNodeMetadataRequestDto) o;
    return Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && flowNodeType == that.flowNodeType;
  }

  @Override
  public String toString() {
    return "FlowNodeMetadataRequestDto{"
        + "flowNodeId='"
        + flowNodeId
        + '\''
        + ", flowNodeInstanceId='"
        + flowNodeInstanceId
        + '\''
        + ", flowNodeType="
        + flowNodeType
        + '}';
  }
}
