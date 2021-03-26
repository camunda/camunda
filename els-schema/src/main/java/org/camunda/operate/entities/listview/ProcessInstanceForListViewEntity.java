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

public class ProcessInstanceForListViewEntity extends OperateZeebeEntity<ProcessInstanceForListViewEntity> {

  private Long processDefinitionKey;
  private String processName;
  private Integer processVersion;
  private String bpmnProcessId;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private ProcessInstanceState state;

  private List<String> batchOperationIds;

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

    ProcessInstanceForListViewEntity that = (ProcessInstanceForListViewEntity) o;

    if (processDefinitionKey != null ? !processDefinitionKey.equals(that.processDefinitionKey) : that.processDefinitionKey != null)
      return false;
    if (processName != null ? !processName.equals(that.processName) : that.processName != null)
      return false;
    if (processVersion != null ? !processVersion.equals(that.processVersion) : that.processVersion != null)
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
    result = 31 * result + (processDefinitionKey != null ? processDefinitionKey.hashCode() : 0);
    result = 31 * result + (processName != null ? processName.hashCode() : 0);
    result = 31 * result + (processVersion != null ? processVersion.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (batchOperationIds != null ? batchOperationIds.hashCode() : 0);
    result = 31 * result + (joinRelation != null ? joinRelation.hashCode() : 0);
    return result;
  }
}
