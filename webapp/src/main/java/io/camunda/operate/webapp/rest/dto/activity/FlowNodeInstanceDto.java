/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.activity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;

public class FlowNodeInstanceDto {

  private String id;

  private FlowNodeType type;

  private FlowNodeState state;

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

  public FlowNodeState getState() {
    return state;
  }

  public void setState(FlowNodeState state) {
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

  public static FlowNodeInstanceDto createFrom(FlowNodeInstanceEntity flowNodeInstanceEntity) {
    FlowNodeInstanceDto flowNode = new FlowNodeInstanceDto();
    flowNode.setId(flowNodeInstanceEntity.getId());
    flowNode.setFlowNodeId(flowNodeInstanceEntity.getFlowNodeId());
    flowNode.setStartDate(flowNodeInstanceEntity.getStartDate());
    flowNode.setEndDate(flowNodeInstanceEntity.getEndDate());
    if (flowNodeInstanceEntity.getIncidentKey() != null) {
      flowNode.setState(FlowNodeState.INCIDENT);
    } else {
      flowNode.setState(flowNodeInstanceEntity.getState());
    }
    flowNode.setType(flowNodeInstanceEntity.getType());
    flowNode.setSortValues(flowNodeInstanceEntity.getSortValues());
    flowNode.setTreePath(flowNodeInstanceEntity.getTreePath());
    return flowNode;
  }

  public static List<FlowNodeInstanceDto> createFrom(List<FlowNodeInstanceEntity> flowNodeInstanceEntities) {
    List<FlowNodeInstanceDto> result = new ArrayList<>();
    if (flowNodeInstanceEntities != null) {
      for (FlowNodeInstanceEntity flowNodeInstanceEntity: flowNodeInstanceEntities) {
        if (flowNodeInstanceEntity != null) {
          result.add(createFrom(flowNodeInstanceEntity));
        }
      }
    }
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
