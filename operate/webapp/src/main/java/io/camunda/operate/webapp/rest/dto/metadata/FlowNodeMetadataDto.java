/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.metadata;

import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FlowNodeMetadataDto {

  /**
   * These fields show, which exactly metadata is returned. E.g. in case flowNodeInstanceId is not
   * null, then metadata is about specific instance. In case flowNodeId and flowNodeType are not
   * null, then we returned the number of instances with the given flowNodeId and type.
   */
  private String flowNodeInstanceId;

  private String flowNodeId;
  private FlowNodeType flowNodeType;

  private Long instanceCount;

  private List<FlowNodeInstanceBreadcrumbEntryDto> breadcrumb = new ArrayList<>();

  private FlowNodeInstanceMetadata instanceMetadata;

  private Long incidentCount;

  private IncidentDto incident;

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

  public FlowNodeInstanceMetadata getInstanceMetadata() {
    return instanceMetadata;
  }

  public FlowNodeMetadataDto setInstanceMetadata(final FlowNodeInstanceMetadata instanceMetadata) {
    this.instanceMetadata = instanceMetadata;
    return this;
  }

  public Long getIncidentCount() {
    return incidentCount;
  }

  public FlowNodeMetadataDto setIncidentCount(final Long incidentCount) {
    this.incidentCount = incidentCount;
    return this;
  }

  public IncidentDto getIncident() {
    return incident;
  }

  public FlowNodeMetadataDto setIncident(final IncidentDto incident) {
    this.incident = incident;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        flowNodeInstanceId,
        flowNodeId,
        flowNodeType,
        instanceCount,
        breadcrumb,
        instanceMetadata,
        incidentCount,
        incident);
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
    return Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && flowNodeType == that.flowNodeType
        && Objects.equals(instanceCount, that.instanceCount)
        && Objects.equals(breadcrumb, that.breadcrumb)
        && Objects.equals(instanceMetadata, that.instanceMetadata)
        && Objects.equals(incidentCount, that.incidentCount)
        && Objects.equals(incident, that.incident);
  }

  @Override
  public String toString() {
    return "FlowNodeMetadataDto{"
        + "flowNodeInstanceId='"
        + flowNodeInstanceId
        + '\''
        + ", flowNodeId='"
        + flowNodeId
        + '\''
        + ", flowNodeType="
        + flowNodeType
        + ", instanceCount="
        + instanceCount
        + ", breadcrumb="
        + breadcrumb
        + ", instanceMetadata="
        + instanceMetadata
        + ", incidentCount="
        + incidentCount
        + ", incident="
        + incident
        + '}';
  }
}
