/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.entities.listview;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import io.camunda.operate.entities.OperateZeebeEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;
import java.util.Objects;

public class ProcessInstanceForListViewEntity extends OperateZeebeEntity<ProcessInstanceForListViewEntity> {

  private Long processDefinitionKey;
  private String processName;
  private Integer processVersion;
  private String bpmnProcessId;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private ProcessInstanceState state;

  private List<String> batchOperationIds;

  private String parentProcessInstanceId;

  private String parentFlowNodeInstanceId;

  private ListViewJoinRelation joinRelation = new ListViewJoinRelation(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);

  @JsonIgnore
  private Object[] sortValues;

  public Long getProcessInstanceKey() {
    return getKey();
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.setKey(processInstanceKey);
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessName() {
    return processName;
  }

  public void setProcessName(String processName) {
    this.processName = processName;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public void setProcessVersion(Integer processVersion) {
    this.processVersion = processVersion;
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

  public ProcessInstanceState getState() {
    return state;
  }

  public void setState(ProcessInstanceState state) {
    this.state = state;
  }

  public List<String> getBatchOperationIds() {
    return batchOperationIds;
  }

  public void setBatchOperationIds(List<String> batchOperationIds) {
    this.batchOperationIds = batchOperationIds;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getParentProcessInstanceId() {
    return parentProcessInstanceId;
  }

  public ProcessInstanceForListViewEntity setParentProcessInstanceId(
      final String parentProcessInstanceId) {
    this.parentProcessInstanceId = parentProcessInstanceId;
    return this;
  }

  public String getParentFlowNodeInstanceId() {
    return parentFlowNodeInstanceId;
  }

  public ProcessInstanceForListViewEntity setParentFlowNodeInstanceId(
      final String parentFlowNodeInstanceId) {
    this.parentFlowNodeInstanceId = parentFlowNodeInstanceId;
    return this;
  }

  public ListViewJoinRelation getJoinRelation() {
    return joinRelation;
  }

  public void setJoinRelation(ListViewJoinRelation joinRelation) {
    this.joinRelation = joinRelation;
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
    final ProcessInstanceForListViewEntity that = (ProcessInstanceForListViewEntity) o;
    return Objects.equals(processDefinitionKey, that.processDefinitionKey) &&
        Objects.equals(processName, that.processName) &&
        Objects.equals(processVersion, that.processVersion) &&
        Objects.equals(bpmnProcessId, that.bpmnProcessId) &&
        Objects.equals(startDate, that.startDate) &&
        Objects.equals(endDate, that.endDate) &&
        state == that.state &&
        Objects.equals(batchOperationIds, that.batchOperationIds) &&
        Objects.equals(parentProcessInstanceId, that.parentProcessInstanceId) &&
        Objects.equals(parentFlowNodeInstanceId, that.parentFlowNodeInstanceId) &&
        Objects.equals(joinRelation, that.joinRelation) &&
        Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result = Objects
        .hash(super.hashCode(), processDefinitionKey, processName, processVersion, bpmnProcessId,
            startDate, endDate, state, batchOperationIds, parentProcessInstanceId,
            parentFlowNodeInstanceId, joinRelation);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }
}
