/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.webapp.rest.dto.OperationDto;

public class ListViewProcessInstanceDto {

  private String id;

  private String processId;
  private String processName;
  private Integer processVersion;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private ProcessInstanceStateDto state;

  private String bpmnProcessId;

  private boolean hasActiveOperation = false;

  private List<OperationDto> operations = new ArrayList<>();

  /**
   * Sort values, define the position of process instance in the list and may be used to search
   * for previous or following page.
   */
  private String[] sortValues;

  public String getId() {
    return id;
  }

  public ListViewProcessInstanceDto setId(String id) {
    this.id = id;
    return this;
  }

  public String getProcessId() {
    return processId;
  }

  public ListViewProcessInstanceDto setProcessId(String processId) {
    this.processId = processId;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public ListViewProcessInstanceDto setProcessName(String processName) {
    this.processName = processName;
    return this;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ListViewProcessInstanceDto setProcessVersion(Integer processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public ListViewProcessInstanceDto setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public ListViewProcessInstanceDto setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public ProcessInstanceStateDto getState() {
    return state;
  }

  public ListViewProcessInstanceDto setState(ProcessInstanceStateDto state) {
    this.state = state;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ListViewProcessInstanceDto setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public boolean isHasActiveOperation() {
    return hasActiveOperation;
  }

  public ListViewProcessInstanceDto setHasActiveOperation(boolean hasActiveOperation) {
    this.hasActiveOperation = hasActiveOperation;
    return this;
  }

  public List<OperationDto> getOperations() {
    return operations;
  }

  public ListViewProcessInstanceDto setOperations(List<OperationDto> operations) {
    this.operations = operations;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public ListViewProcessInstanceDto setSortValues(String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public static ListViewProcessInstanceDto createFrom(ProcessInstanceForListViewEntity processInstanceEntity, boolean containsIncident,
    List<OperationEntity> operations) {
    if (processInstanceEntity == null) {
      return null;
    }
    ListViewProcessInstanceDto processInstance = new ListViewProcessInstanceDto();
    processInstance.setId(processInstanceEntity.getId())
      .setStartDate(processInstanceEntity.getStartDate())
      .setEndDate(processInstanceEntity.getEndDate());
    if (!containsIncident) {
      processInstance.setState(ProcessInstanceStateDto.getState(processInstanceEntity.getState()));
    } else {
      processInstance.setState(ProcessInstanceStateDto.INCIDENT);
    }

    processInstance.setProcessId(ConversionUtils.toStringOrNull(processInstanceEntity.getProcessDefinitionKey()))
      .setBpmnProcessId(processInstanceEntity.getBpmnProcessId())
      .setProcessName(processInstanceEntity.getProcessName())
      .setProcessVersion(processInstanceEntity.getProcessVersion())
      .setOperations(OperationDto.createFrom(operations));
    if (operations != null) {
      processInstance.setHasActiveOperation(operations.stream().anyMatch(
        o ->
          o.getState().equals(OperationState.SCHEDULED)
          || o.getState().equals(OperationState.LOCKED)
          || o.getState().equals(OperationState.SENT)));
    }
    //convert to String[]
    if (processInstanceEntity.getSortValues() != null) {
      processInstance.setSortValues(Arrays.stream(processInstanceEntity.getSortValues())
          .map(String::valueOf)
          .toArray(String[]::new));
    }
    return processInstance;
  }

  public static List<ListViewProcessInstanceDto> createFrom(List<ProcessInstanceForListViewEntity> processInstanceEntities,
    Set<Long> instancesWithIncidents, Map<Long, List<OperationEntity>> operationsPerWorfklowInstance) {
    List<ListViewProcessInstanceDto> result = new ArrayList<>();
    if (processInstanceEntities != null) {
      for (ProcessInstanceForListViewEntity processInstanceEntity: processInstanceEntities) {
        if (processInstanceEntity != null) {
          final ListViewProcessInstanceDto instanceDto = createFrom(processInstanceEntity, instancesWithIncidents.contains(processInstanceEntity.getProcessInstanceKey()),
            operationsPerWorfklowInstance.get(processInstanceEntity.getProcessInstanceKey()));
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

    ListViewProcessInstanceDto that = (ListViewProcessInstanceDto) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (processId != null ? !processId.equals(that.processId) : that.processId != null)
      return false;
    if (processName != null ? !processName.equals(that.processName) : that.processName != null)
      return false;
    if (processVersion != null ? !processVersion.equals(that.processVersion) : that.processVersion != null)
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
    result = 31 * result + (processId != null ? processId.hashCode() : 0);
    result = 31 * result + (processName != null ? processName.hashCode() : 0);
    result = 31 * result + (processVersion != null ? processVersion.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (operations != null ? operations.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return String.format("ListViewProcessInstanceDto %s (%s)", processName, processId);
  }
}
