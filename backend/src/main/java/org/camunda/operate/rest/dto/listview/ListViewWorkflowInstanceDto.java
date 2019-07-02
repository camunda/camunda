/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.listview;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.rest.dto.OperationDto;
import org.camunda.operate.util.StringUtils;

public class ListViewWorkflowInstanceDto {

  private String id;

  private String workflowId;
  private String workflowName;
  private Integer workflowVersion;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private WorkflowInstanceStateDto state;

  private String bpmnProcessId;

  private boolean hasActiveOperation = false;

  private List<OperationDto> operations = new ArrayList<>();

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

  public WorkflowInstanceStateDto getState() {
    return state;
  }

  public void setState(WorkflowInstanceStateDto state) {
    this.state = state;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public boolean isHasActiveOperation() {
    return hasActiveOperation;
  }

  public void setHasActiveOperation(boolean hasActiveOperation) {
    this.hasActiveOperation = hasActiveOperation;
  }

  public List<OperationDto> getOperations() {
    return operations;
  }

  public void setOperations(List<OperationDto> operations) {
    this.operations = operations;
  }

  public static ListViewWorkflowInstanceDto createFrom(WorkflowInstanceForListViewEntity workflowInstanceEntity, boolean containsIncident,
    List<OperationEntity> operations) {
    if (workflowInstanceEntity == null) {
      return null;
    }
    ListViewWorkflowInstanceDto workflowInstance = new ListViewWorkflowInstanceDto();
    workflowInstance.setId(workflowInstanceEntity.getId());
    workflowInstance.setStartDate(workflowInstanceEntity.getStartDate());
    workflowInstance.setEndDate(workflowInstanceEntity.getEndDate());
    if (!containsIncident) {
      workflowInstance.setState(WorkflowInstanceStateDto.getState(workflowInstanceEntity.getState()));
    } else {
      workflowInstance.setState(WorkflowInstanceStateDto.INCIDENT);
    }

    workflowInstance.setWorkflowId(StringUtils.toStringOrNull(workflowInstanceEntity.getWorkflowId()));
    workflowInstance.setBpmnProcessId(workflowInstanceEntity.getBpmnProcessId());
    workflowInstance.setWorkflowName(workflowInstanceEntity.getWorkflowName());
    workflowInstance.setWorkflowVersion(workflowInstanceEntity.getWorkflowVersion());
    workflowInstance.setOperations(OperationDto.createFrom(operations));
    if (operations != null) {
      workflowInstance.setHasActiveOperation(operations.stream().anyMatch(
        o ->
          o.getState().equals(OperationState.SCHEDULED)
          || o.getState().equals(OperationState.LOCKED)
          || o.getState().equals(OperationState.SENT)));
    }
    return workflowInstance;
  }

  public static List<ListViewWorkflowInstanceDto> createFrom(List<WorkflowInstanceForListViewEntity> workflowInstanceEntities,
    Set<Long> instancesWithIncidents, Map<Long, List<OperationEntity>> operationsPerWorfklowInstance) {
    List<ListViewWorkflowInstanceDto> result = new ArrayList<>();
    if (workflowInstanceEntities != null) {
      for (WorkflowInstanceForListViewEntity workflowInstanceEntity: workflowInstanceEntities) {
        if (workflowInstanceEntity != null) {
          final ListViewWorkflowInstanceDto instanceDto = createFrom(workflowInstanceEntity, instancesWithIncidents.contains(workflowInstanceEntity.getWorkflowInstanceId()),
            operationsPerWorfklowInstance.get(workflowInstanceEntity.getWorkflowInstanceId()));
          result.add(instanceDto);
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ListViewWorkflowInstanceDto that = (ListViewWorkflowInstanceDto) o;

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
    return operations != null ? operations.equals(that.operations) : that.operations == null;
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
    result = 31 * result + (operations != null ? operations.hashCode() : 0);
    return result;
  }
  
  @Override
  public String toString() {
    return String.format("ListViewWorkflowInstanceDto %s (%s)", workflowName, workflowId);
  }
}
