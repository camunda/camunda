/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.entities;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class FlowNodeInstanceEntity extends OperateZeebeEntity<FlowNodeInstanceEntity> {

  private String flowNodeId;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private FlowNodeState state;
  private FlowNodeType type;
  private Long incidentKey;
  private Long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private Long processDefinitionKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private String bpmnProcessId;

  private String treePath;
  private int level;
  private Long position;
  private boolean incident;
  private String tenantId = DEFAULT_TENANT_ID;

  @JsonIgnore private Object[] sortValues;

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public FlowNodeInstanceEntity setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public FlowNodeInstanceEntity setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public FlowNodeInstanceEntity setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public FlowNodeState getState() {
    return state;
  }

  public FlowNodeInstanceEntity setState(FlowNodeState state) {
    this.state = state;
    return this;
  }

  public FlowNodeType getType() {
    return type;
  }

  public FlowNodeInstanceEntity setType(FlowNodeType type) {
    this.type = type;
    return this;
  }

  public Long getIncidentKey() {
    return incidentKey;
  }

  public FlowNodeInstanceEntity setIncidentKey(Long incidentKey) {
    this.incidentKey = incidentKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public FlowNodeInstanceEntity setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public FlowNodeInstanceEntity setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public FlowNodeInstanceEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public FlowNodeInstanceEntity setTreePath(String treePath) {
    this.treePath = treePath;
    return this;
  }

  public int getLevel() {
    return level;
  }

  public FlowNodeInstanceEntity setLevel(int level) {
    this.level = level;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public FlowNodeInstanceEntity setPosition(Long position) {
    this.position = position;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public FlowNodeInstanceEntity setSortValues(Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public boolean isIncident() {
    return incident;
  }

  public FlowNodeInstanceEntity setIncident(final boolean incident) {
    this.incident = incident;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public FlowNodeInstanceEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            super.hashCode(),
            flowNodeId,
            startDate,
            endDate,
            state,
            type,
            incidentKey,
            processInstanceKey,
            processDefinitionKey,
            bpmnProcessId,
            treePath,
            level,
            position,
            incident,
            tenantId);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public boolean equals(Object o) {
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
    return level == that.level
        && incident == that.incident
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && state == that.state
        && type == that.type
        && Objects.equals(incidentKey, that.incidentKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(treePath, that.treePath)
        && Objects.equals(position, that.position)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(sortValues, that.sortValues);
  }
}
