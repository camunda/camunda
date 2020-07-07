/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.entities;

import java.util.Objects;

public class FlowNodeInstanceEntity extends TasklistZeebeEntity<FlowNodeInstanceEntity> {

  private String parentFlowNodeId;
  private String workflowInstanceId;
  private Long position;
  private FlowNodeType type;

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public FlowNodeInstanceEntity setWorkflowInstanceId(final String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
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
    return Objects.equals(parentFlowNodeId, that.parentFlowNodeId)
        && Objects.equals(workflowInstanceId, that.workflowInstanceId)
        && Objects.equals(position, that.position)
        && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), parentFlowNodeId, workflowInstanceId, position, type);
  }
}
