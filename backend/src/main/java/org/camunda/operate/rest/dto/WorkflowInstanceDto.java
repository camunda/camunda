/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;


public class WorkflowInstanceDto {

  private String id;

  private String workflowId;
  private String workflowName;
  private Integer workflowVersion;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private WorkflowInstanceState state;

  private String bpmnProcessId;

  private List<IncidentDto> incidents = new ArrayList<>();

  private List<OperationDto> operations = new ArrayList<>();

  private List<SequenceFlowDto> sequenceFlows = new ArrayList<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
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

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public List<IncidentDto> getIncidents() {
    return incidents;
  }

  public void setIncidents(List<IncidentDto> incidents) {
    this.incidents = incidents;
  }

  public List<OperationDto> getOperations() {
    return operations;
  }

  public void setOperations(List<OperationDto> operations) {
    this.operations = operations;
  }

  public List<SequenceFlowDto> getSequenceFlows() {
    return sequenceFlows;
  }

  public void setSequenceFlows(List<SequenceFlowDto> sequenceFlows) {
    this.sequenceFlows = sequenceFlows;
  }

  public static WorkflowInstanceDto createFrom(WorkflowInstanceEntity workflowInstanceEntity) {
    if (workflowInstanceEntity == null) {
      return null;
    }
    WorkflowInstanceDto workflowInstance = new WorkflowInstanceDto();
    workflowInstance.setId(workflowInstanceEntity.getId());
    workflowInstance.setStartDate(workflowInstanceEntity.getStartDate());
    workflowInstance.setEndDate(workflowInstanceEntity.getEndDate());
    workflowInstance.setState(workflowInstanceEntity.getState());
    workflowInstance.setWorkflowId(workflowInstanceEntity.getWorkflowId());
    workflowInstance.setBpmnProcessId(workflowInstanceEntity.getBpmnProcessId());
    workflowInstance.setWorkflowName(workflowInstanceEntity.getWorkflowName());
    workflowInstance.setWorkflowVersion(workflowInstanceEntity.getWorkflowVersion());
    workflowInstance.setIncidents(IncidentDto.createFrom(workflowInstanceEntity.getIncidents()));
    workflowInstance.setOperations(OperationDto.createFrom(workflowInstanceEntity.getOperations()));
    workflowInstance.setSequenceFlows(SequenceFlowDto.createFrom(workflowInstanceEntity.getSequenceFlows()));
    return workflowInstance;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    WorkflowInstanceDto that = (WorkflowInstanceDto) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (workflowId != null ? !workflowId.equals(that.workflowId) : that.workflowId != null)
      return false;
    if (workflowName != null ? !workflowName.equals(that.workflowName) : that.workflowName != null)
      return false;
    if (workflowVersion != null ? !workflowVersion.equals(that.workflowVersion) : that.workflowVersion != null)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (state != that.state)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (incidents != null ? !incidents.equals(that.incidents) : that.incidents != null)
      return false;
    if (operations != null ? !operations.equals(that.operations) : that.operations != null)
      return false;
    return sequenceFlows != null ? sequenceFlows.equals(that.sequenceFlows) : that.sequenceFlows == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (workflowId != null ? workflowId.hashCode() : 0);
    result = 31 * result + (workflowName != null ? workflowName.hashCode() : 0);
    result = 31 * result + (workflowVersion != null ? workflowVersion.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (incidents != null ? incidents.hashCode() : 0);
    result = 31 * result + (operations != null ? operations.hashCode() : 0);
    result = 31 * result + (sequenceFlows != null ? sequenceFlows.hashCode() : 0);
    return result;
  }
}
