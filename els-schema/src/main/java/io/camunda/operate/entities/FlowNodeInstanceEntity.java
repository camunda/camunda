/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

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
  private Long processDefinitionKey;
  private String bpmnProcessId;
  private String treePath;
  private int level;
  private Long position;
  private boolean incident;

  @JsonIgnore
  private Object[] sortValues;

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

  public FlowNodeState getState() {
    return state;
  }

  public void setState(FlowNodeState state) {
    this.state = state;
  }

  public FlowNodeType getType() {
    return type;
  }

  public void setType(FlowNodeType type) {
    this.type = type;
  }

  public Long getIncidentKey() {
    return incidentKey;
  }

  public void setIncidentKey(Long incidentKey) {
    this.incidentKey = incidentKey;
  }

  public String getTreePath() {
    return treePath;
  }

  public void setTreePath(final String treePath) {
    this.treePath = treePath;
  }

  public int getLevel() {
    return level;
  }

  public void setLevel(final int level) {
    this.level = level;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public Long getPosition() {
    return position;
  }

  public void setPosition(Long position) {
    this.position = position;
  }

  public boolean isIncident() {
    return incident;
  }

  public FlowNodeInstanceEntity setIncident(final boolean incident) {
    this.incident = incident;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public void setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
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
    return level == that.level &&
        incident == that.incident &&
        Objects.equals(flowNodeId, that.flowNodeId) &&
        Objects.equals(startDate, that.startDate) &&
        Objects.equals(endDate, that.endDate) &&
        state == that.state &&
        type == that.type &&
        Objects.equals(incidentKey, that.incidentKey) &&
        Objects.equals(processInstanceKey, that.processInstanceKey) &&
        Objects.equals(processDefinitionKey, that.processDefinitionKey) &&
        Objects.equals(bpmnProcessId, that.bpmnProcessId) &&
        Objects.equals(treePath, that.treePath) &&
        Objects.equals(position, that.position) &&
        Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result = Objects
        .hash(super.hashCode(), flowNodeId, startDate, endDate, state, type, incidentKey,
            processInstanceKey, processDefinitionKey, bpmnProcessId, treePath, level, position, incident);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }
}
