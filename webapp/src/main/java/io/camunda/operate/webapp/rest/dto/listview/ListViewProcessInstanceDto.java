/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceReferenceDto;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.zeebeimport.util.TreePath;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

  private String parentInstanceId;

  private String rootInstanceId;

  private List<ProcessInstanceReferenceDto> callHierarchy = new ArrayList<>();

  /**
   * Sort values, define the position of process instance in the list and may be used to search
   * for previous or following page.
   */
  private String[] sortValues;

  private Set<String> permissions;

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

  public String getParentInstanceId() {
    return parentInstanceId;
  }

  public ListViewProcessInstanceDto setParentInstanceId(final String parentInstanceId) {
    this.parentInstanceId = parentInstanceId;
    return this;
  }

  public List<ProcessInstanceReferenceDto> getCallHierarchy() {
    return callHierarchy;
  }

  public ListViewProcessInstanceDto setCallHierarchy(
      final List<ProcessInstanceReferenceDto> callHierarchy) {
    this.callHierarchy = callHierarchy;
    return this;
  }

  public String getRootInstanceId() {
    return rootInstanceId;
  }

  public ListViewProcessInstanceDto setRootInstanceId(final String rootInstanceId) {
    this.rootInstanceId = rootInstanceId;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public ListViewProcessInstanceDto setSortValues(String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<String> permissions) {
    this.permissions = permissions;
  }

  public static ListViewProcessInstanceDto createFrom(ProcessInstanceForListViewEntity processInstanceEntity,
      List<OperationEntity> operations) {
    return createFrom(processInstanceEntity, operations, null, null);
  }

  public static ListViewProcessInstanceDto createFrom(ProcessInstanceForListViewEntity processInstanceEntity,
      List<OperationEntity> operations, List<ProcessInstanceReferenceDto> callHierarchy) {
    return createFrom(processInstanceEntity, operations, callHierarchy, null);
  }

  public static ListViewProcessInstanceDto createFrom(ProcessInstanceForListViewEntity processInstanceEntity,
    List<OperationEntity> operations, List<ProcessInstanceReferenceDto> callHierarchy, PermissionsService permissionsService) {
    if (processInstanceEntity == null) {
      return null;
    }
    ListViewProcessInstanceDto processInstance = new ListViewProcessInstanceDto();
    processInstance.setId(processInstanceEntity.getId())
      .setStartDate(processInstanceEntity.getStartDate())
      .setEndDate(processInstanceEntity.getEndDate());
    if (processInstanceEntity.getState() == ProcessInstanceState.ACTIVE && processInstanceEntity
        .isIncident()) {
      processInstance.setState(ProcessInstanceStateDto.INCIDENT);
    } else {
      processInstance.setState(ProcessInstanceStateDto.getState(processInstanceEntity.getState()));
    }

    processInstance.setProcessId(ConversionUtils.toStringOrNull(processInstanceEntity.getProcessDefinitionKey()))
      .setBpmnProcessId(processInstanceEntity.getBpmnProcessId())
      .setProcessName(processInstanceEntity.getProcessName())
      .setProcessVersion(processInstanceEntity.getProcessVersion())
      .setOperations(DtoCreator.create(operations, OperationDto.class));
    if (operations != null) {
      processInstance.setHasActiveOperation(operations.stream().anyMatch(
        o ->
          o.getState().equals(OperationState.SCHEDULED)
          || o.getState().equals(OperationState.LOCKED)
          || o.getState().equals(OperationState.SENT)));
    }
    if (processInstanceEntity.getParentProcessInstanceKey() != null) {
      processInstance
          .setParentInstanceId(String.valueOf(processInstanceEntity.getParentProcessInstanceKey()));
    }
    //convert to String[]
    if (processInstanceEntity.getSortValues() != null) {
      processInstance.setSortValues(Arrays.stream(processInstanceEntity.getSortValues())
          .map(String::valueOf)
          .toArray(String[]::new));
    }

    if (processInstanceEntity.getTreePath() != null) {
      final String rootInstanceId = new TreePath(processInstanceEntity.getTreePath())
          .extractRootInstanceId();
      if (!processInstanceEntity.getId().equals(rootInstanceId)) {
        processInstance.setRootInstanceId(rootInstanceId);
      }
    }
    processInstance.setCallHierarchy(callHierarchy);
    processInstance.setPermissions(permissionsService == null ? new HashSet<>() :
        permissionsService.getProcessDefinitionPermission(processInstanceEntity.getBpmnProcessId()));
    return processInstance;
  }

  public static List<ListViewProcessInstanceDto> createFrom(
      List<ProcessInstanceForListViewEntity> processInstanceEntities,
      Map<Long, List<OperationEntity>> operationsPerProcessInstance) {
    if (processInstanceEntities == null) {
      return new ArrayList<>();
    }
    return processInstanceEntities.stream().filter(item -> item != null)
        .map(item -> createFrom(item,
            operationsPerProcessInstance.get(item.getProcessInstanceKey())))
        .collect(Collectors.toList());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ListViewProcessInstanceDto that = (ListViewProcessInstanceDto) o;
    return hasActiveOperation == that.hasActiveOperation &&
        Objects.equals(id, that.id) &&
        Objects.equals(processId, that.processId) &&
        Objects.equals(processName, that.processName) &&
        Objects.equals(processVersion, that.processVersion) &&
        Objects.equals(startDate, that.startDate) &&
        Objects.equals(endDate, that.endDate) &&
        state == that.state &&
        Objects.equals(bpmnProcessId, that.bpmnProcessId) &&
        Objects.equals(operations, that.operations) &&
        Objects.equals(parentInstanceId, that.parentInstanceId) &&
        Objects.equals(rootInstanceId, that.rootInstanceId) &&
        Objects.equals(callHierarchy, that.callHierarchy) &&
        Arrays.equals(sortValues, that.sortValues) &&
        Objects.equals(permissions, that.permissions);
  }

  @Override
  public int hashCode() {
    int result = Objects
        .hash(id, processId, processName, processVersion, startDate, endDate, state, bpmnProcessId,
            hasActiveOperation, operations, parentInstanceId, rootInstanceId, callHierarchy, permissions);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public String toString() {
    return String.format("ListViewProcessInstanceDto %s (%s)", processName, processId);
  }
}
