/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.camunda.operate.entities.FlowNodeType;

public class FlowNodeMetadataDto {

  /**
   * These fields show, which exactly metadata is returned. E.g. in case flowNodeInstanceId is not null,
   * then metadata is about specific instance. In case flowNodeId and flowNodeType are not null, then
   * we returned the number of instances with the given flowNodeId and type.
   */
  private String flowNodeInstanceId;
  private String flowNodeId;
  private FlowNodeType flowNodeType;

  private Long instanceCount;

  private List<FlowNodeInstanceBreadcrumbEntryDto> breadcrumb = new ArrayList<>();

  private FlowNodeInstanceMetadataDto instanceMetadata;

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public FlowNodeMetadataDto setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public FlowNodeMetadataDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public FlowNodeType getFlowNodeType() {
    return flowNodeType;
  }

  public FlowNodeMetadataDto setFlowNodeType(final FlowNodeType flowNodeType) {
    this.flowNodeType = flowNodeType;
    return this;
  }

  public Long getInstanceCount() {
    return instanceCount;
  }

  public FlowNodeMetadataDto setInstanceCount(final Long instanceCount) {
    this.instanceCount = instanceCount;
    return this;
  }

  public List<FlowNodeInstanceBreadcrumbEntryDto> getBreadcrumb() {
    return breadcrumb;
  }

  public FlowNodeMetadataDto setBreadcrumb(
      final List<FlowNodeInstanceBreadcrumbEntryDto> breadcrumb) {
    this.breadcrumb = breadcrumb;
    return this;
  }

  public FlowNodeInstanceMetadataDto getInstanceMetadata() {
    return instanceMetadata;
  }

  public FlowNodeMetadataDto setInstanceMetadata(
      final FlowNodeInstanceMetadataDto instanceMetadata) {
    this.instanceMetadata = instanceMetadata;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeMetadataDto that = (FlowNodeMetadataDto) o;
    return Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId) &&
        Objects.equals(flowNodeId, that.flowNodeId) &&
        flowNodeType == that.flowNodeType &&
        Objects.equals(instanceCount, that.instanceCount) &&
        Objects.equals(breadcrumb, that.breadcrumb) &&
        Objects.equals(instanceMetadata, that.instanceMetadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(flowNodeInstanceId, flowNodeId, flowNodeType, instanceCount, breadcrumb,
        instanceMetadata);
  }

  @Override
  public String toString() {
    return "FlowNodeMetadataDto{" +
        "flowNodeInstanceId='" + flowNodeInstanceId + '\'' +
        ", flowNodeId='" + flowNodeId + '\'' +
        ", flowNodeType=" + flowNodeType +
        ", instanceCount=" + instanceCount +
        ", breadcrumb=" + breadcrumb +
        ", instanceMetadata=" + instanceMetadata +
        '}';
  }
}
