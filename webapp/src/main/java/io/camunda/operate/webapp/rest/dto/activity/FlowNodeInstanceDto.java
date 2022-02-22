/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.activity;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.webapp.rest.dto.CreatableFromEntity;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class FlowNodeInstanceDto implements
    CreatableFromEntity<FlowNodeInstanceDto, FlowNodeInstanceEntity> {

  private String id;

  private FlowNodeType type;

  private FlowNodeStateDto state;

  private String flowNodeId;

  private OffsetDateTime startDate;

  private OffsetDateTime endDate;

  private String treePath;

  /**
   * Sort values, define the position of batch operation in the list and may be used to search for previous of following page.
   */
  private Object[] sortValues;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public FlowNodeStateDto getState() {
    return state;
  }

  public void setState(FlowNodeStateDto state) {
    this.state = state;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public FlowNodeType getType() {
    return type;
  }

  public void setType(FlowNodeType type) {
    this.type = type;
  }

  public String getTreePath() {
    return treePath;
  }

  public void setTreePath(final String treePath) {
    this.treePath = treePath;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public void setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
  }

  @Override
  public FlowNodeInstanceDto fillFrom(final FlowNodeInstanceEntity flowNodeInstanceEntity) {
    this.setId(flowNodeInstanceEntity.getId());
    this.setFlowNodeId(flowNodeInstanceEntity.getFlowNodeId());
    this.setStartDate(flowNodeInstanceEntity.getStartDate());
    this.setEndDate(flowNodeInstanceEntity.getEndDate());
    if (flowNodeInstanceEntity.getState() == FlowNodeState.ACTIVE && flowNodeInstanceEntity
        .isIncident()) {
      this.setState(FlowNodeStateDto.INCIDENT);
    } else {
      this.setState(FlowNodeStateDto.getState(flowNodeInstanceEntity.getState()));
    }
    this.setType(flowNodeInstanceEntity.getType());
    this.setSortValues(flowNodeInstanceEntity.getSortValues());
    this.setTreePath(flowNodeInstanceEntity.getTreePath());
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
    final FlowNodeInstanceDto that = (FlowNodeInstanceDto) o;
    return Objects.equals(id, that.id) &&
        type == that.type &&
        state == that.state &&
        Objects.equals(flowNodeId, that.flowNodeId) &&
        Objects.equals(startDate, that.startDate) &&
        Objects.equals(endDate, that.endDate) &&
        Objects.equals(treePath, that.treePath) &&
        Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, type, state, flowNodeId, startDate, endDate, treePath);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

}
