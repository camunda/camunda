/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FlowNodeInstanceDto {

  private String id;

  private FlowNodeType type;

  private FlowNodeStateDto state;

  private String flowNodeId;

  private OffsetDateTime startDate;

  private OffsetDateTime endDate;

  private String treePath;

  /**
   * Sort values, define the position of batch operation in the list and may be used to search for
   * previous of following page.
   */
  private SortValuesWrapper[] sortValues;

  public static FlowNodeInstanceDto createFrom(
      final FlowNodeInstanceEntity flowNodeInstanceEntity, final ObjectMapper objectMapper) {
    final FlowNodeInstanceDto instance =
        new FlowNodeInstanceDto()
            .setId(flowNodeInstanceEntity.getId())
            .setFlowNodeId(flowNodeInstanceEntity.getFlowNodeId())
            .setStartDate(flowNodeInstanceEntity.getStartDate())
            .setEndDate(flowNodeInstanceEntity.getEndDate());
    if (flowNodeInstanceEntity.getState() == FlowNodeState.ACTIVE
        && flowNodeInstanceEntity.isIncident()) {
      instance.setState(FlowNodeStateDto.INCIDENT);
    } else {
      instance.setState(FlowNodeStateDto.getState(flowNodeInstanceEntity.getState()));
    }
    instance
        .setType(flowNodeInstanceEntity.getType())
        .setSortValues(
            SortValuesWrapper.createFrom(flowNodeInstanceEntity.getSortValues(), objectMapper))
        .setTreePath(flowNodeInstanceEntity.getTreePath());
    return instance;
  }

  public static List<FlowNodeInstanceDto> createFrom(
      final List<FlowNodeInstanceEntity> flowNodeInstanceEntities,
      final ObjectMapper objectMapper) {
    if (flowNodeInstanceEntities == null) {
      return new ArrayList<>();
    }
    return flowNodeInstanceEntities.stream()
        .filter(item -> item != null)
        .map(item -> createFrom(item, objectMapper))
        .collect(Collectors.toList());
  }

  public String getId() {
    return id;
  }

  public FlowNodeInstanceDto setId(final String id) {
    this.id = id;
    return this;
  }

  public FlowNodeStateDto getState() {
    return state;
  }

  public FlowNodeInstanceDto setState(final FlowNodeStateDto state) {
    this.state = state;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public FlowNodeInstanceDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public FlowNodeInstanceDto setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public FlowNodeInstanceDto setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public FlowNodeType getType() {
    return type;
  }

  public FlowNodeInstanceDto setType(final FlowNodeType type) {
    this.type = type;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public FlowNodeInstanceDto setTreePath(final String treePath) {
    this.treePath = treePath;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public FlowNodeInstanceDto setSortValues(final SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, type, state, flowNodeId, startDate, endDate, treePath);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeInstanceDto that = (FlowNodeInstanceDto) o;
    return Objects.equals(id, that.id)
        && type == that.type
        && state == that.state
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(treePath, that.treePath)
        && Arrays.equals(sortValues, that.sortValues);
  }
}
