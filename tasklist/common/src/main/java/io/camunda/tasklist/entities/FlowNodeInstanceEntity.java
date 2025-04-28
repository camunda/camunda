/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities;

import java.util.Objects;

public class FlowNodeInstanceEntity extends TasklistZeebeEntity<FlowNodeInstanceEntity> {

  private String parentFlowNodeId;
  private String processInstanceId;
  private Long position;
  private FlowNodeType type;
  private FlowNodeState state;

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public FlowNodeInstanceEntity setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public FlowNodeInstanceEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public FlowNodeType getType() {
    return type;
  }

  public FlowNodeInstanceEntity setType(final FlowNodeType type) {
    this.type = type;
    return this;
  }

  public String getParentFlowNodeId() {
    return parentFlowNodeId;
  }

  public FlowNodeInstanceEntity setParentFlowNodeId(final String parentFlowNodeId) {
    this.parentFlowNodeId = parentFlowNodeId;
    return this;
  }

  public FlowNodeState getState() {
    return state;
  }

  public void setState(final FlowNodeState state) {
    this.state = state;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), parentFlowNodeId, processInstanceId, position, type, state);
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
    final FlowNodeInstanceEntity that = (FlowNodeInstanceEntity) o;
    return type == that.type
        && state == that.state
        && Objects.equals(parentFlowNodeId, that.parentFlowNodeId)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(position, that.position);
  }
}
