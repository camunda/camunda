/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

  private Long parentProcessInstanceKey;

  private Long parentFlowNodeInstanceKey;

  private String treePath;

  private boolean incident;

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

  public ProcessInstanceForListViewEntity setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public ProcessInstanceState getState() {
    return state;
  }

  public ProcessInstanceForListViewEntity setState(ProcessInstanceState state) {
    this.state = state;
    return this;
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

  public ProcessInstanceForListViewEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Long getParentProcessInstanceKey() {
    return parentProcessInstanceKey;
  }

  public ProcessInstanceForListViewEntity setParentProcessInstanceKey(
      final Long parentProcessInstanceKey) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    return this;
  }

  public Long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  public ProcessInstanceForListViewEntity setParentFlowNodeInstanceKey(
      final Long parentFlowNodeInstanceKey) {
    this.parentFlowNodeInstanceKey = parentFlowNodeInstanceKey;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public ProcessInstanceForListViewEntity setTreePath(final String treePath) {
    this.treePath = treePath;
    return this;
  }

  public boolean isIncident() {
    return incident;
  }

  public ProcessInstanceForListViewEntity setIncident(final boolean incident) {
    this.incident = incident;
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
    return incident == that.incident &&
        Objects.equals(processDefinitionKey, that.processDefinitionKey) &&
        Objects.equals(processName, that.processName) &&
        Objects.equals(processVersion, that.processVersion) &&
        Objects.equals(bpmnProcessId, that.bpmnProcessId) &&
        Objects.equals(startDate, that.startDate) &&
        Objects.equals(endDate, that.endDate) &&
        state == that.state &&
        Objects.equals(batchOperationIds, that.batchOperationIds) &&
        Objects.equals(parentProcessInstanceKey, that.parentProcessInstanceKey) &&
        Objects.equals(parentFlowNodeInstanceKey, that.parentFlowNodeInstanceKey) &&
        Objects.equals(treePath, that.treePath) &&
        Objects.equals(joinRelation, that.joinRelation) &&
        Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result = Objects
        .hash(super.hashCode(), processDefinitionKey, processName, processVersion, bpmnProcessId,
            startDate, endDate, state, batchOperationIds, parentProcessInstanceKey,
            parentFlowNodeInstanceKey, treePath, incident, joinRelation);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }
}
