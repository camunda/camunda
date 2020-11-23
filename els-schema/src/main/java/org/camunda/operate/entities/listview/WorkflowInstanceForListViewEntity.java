/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities.listview;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.List;

import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.schema.templates.ListViewTemplate;

public class WorkflowInstanceForListViewEntity extends OperateZeebeEntity<WorkflowInstanceForListViewEntity> {

  private Long workflowKey;
  private String workflowName;
  private Integer workflowVersion;
  private String bpmnProcessId;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private WorkflowInstanceState state;

  private List<String> batchOperationIds;

  private ListViewJoinRelation joinRelation = new ListViewJoinRelation(ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION);

  @JsonIgnore
  private Object[] sortValues;

  public Long getWorkflowInstanceKey() {
    return getKey();
  }

  public void setWorkflowInstanceKey(Long workflowInstanceKey) {
    this.setKey(workflowInstanceKey);
  }

  public Long getWorkflowKey() {
    return workflowKey;
  }

  public void setWorkflowKey(Long workflowKey) {
    this.workflowKey = workflowKey;
  }

  public String getWorkflowName() {
    return workflowName;
  }

  public void setWorkflowName(String workflowName) {
    this.workflowName = workflowName;
  }

  public Integer getWorkflowVersion() {
    return workflowVersion;
  }

  public void setWorkflowVersion(Integer workflowVersion) {
    this.workflowVersion = workflowVersion;
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

  public WorkflowInstanceState getState() {
    return state;
  }

  public void setState(WorkflowInstanceState state) {
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
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    WorkflowInstanceForListViewEntity that = (WorkflowInstanceForListViewEntity) o;

    if (workflowKey != null ? !workflowKey.equals(that.workflowKey) : that.workflowKey != null)
      return false;
    if (workflowName != null ? !workflowName.equals(that.workflowName) : that.workflowName != null)
      return false;
    if (workflowVersion != null ? !workflowVersion.equals(that.workflowVersion) : that.workflowVersion != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (state != that.state)
      return false;
    if (batchOperationIds != null ? !batchOperationIds.equals(that.batchOperationIds) : that.batchOperationIds != null)
      return false;
    return joinRelation != null ? joinRelation.equals(that.joinRelation) : that.joinRelation == null;

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (workflowKey != null ? workflowKey.hashCode() : 0);
    result = 31 * result + (workflowName != null ? workflowName.hashCode() : 0);
    result = 31 * result + (workflowVersion != null ? workflowVersion.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (batchOperationIds != null ? batchOperationIds.hashCode() : 0);
    result = 31 * result + (joinRelation != null ? joinRelation.hashCode() : 0);
    return result;
  }
}
