/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceReferenceDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ListViewProcessInstanceDto {

  private String id;

  private String processId;
  private String processName;
  private Integer processVersion;
  private String processVersionTag;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private ProcessInstanceStateDto state;

  private String bpmnProcessId;

  private boolean hasActiveOperation = false;

  private List<OperationDto> operations = new ArrayList<>();

  private String parentInstanceId;

  private String rootInstanceId;

  private List<ProcessInstanceReferenceDto> callHierarchy = new ArrayList<>();

  private String tenantId;

  /**
   * Sort values, define the position of process instance in the list and may be used to search for
   * previous or following page.
   */
  private SortValuesWrapper[] sortValues;

  private Set<String> permissions;

  public static ListViewProcessInstanceDto createFrom(
      final ProcessInstanceForListViewEntity processInstanceEntity,
      final List<OperationEntity> operations,
      final PermissionsService permissionsService,
      final ObjectMapper objectMapper) {
    return createFrom(processInstanceEntity, operations, null, permissionsService, objectMapper);
  }

  public static ListViewProcessInstanceDto createFrom(
      final ProcessInstanceForListViewEntity processInstanceEntity,
      final List<OperationEntity> operations,
      final List<ProcessInstanceReferenceDto> callHierarchy,
      final PermissionsService permissionsService,
      final ObjectMapper objectMapper) {
    if (processInstanceEntity == null) {
      return null;
    }
    final ListViewProcessInstanceDto processInstance = new ListViewProcessInstanceDto();
    processInstance
        .setId(processInstanceEntity.getId())
        .setStartDate(processInstanceEntity.getStartDate())
        .setEndDate(processInstanceEntity.getEndDate());
    if (processInstanceEntity.getState() == ProcessInstanceState.ACTIVE
        && processInstanceEntity.isIncident()) {
      processInstance.setState(ProcessInstanceStateDto.INCIDENT);
    } else {
      processInstance.setState(ProcessInstanceStateDto.getState(processInstanceEntity.getState()));
    }

    processInstance
        .setProcessId(
            ConversionUtils.toStringOrNull(processInstanceEntity.getProcessDefinitionKey()))
        .setBpmnProcessId(processInstanceEntity.getBpmnProcessId())
        .setProcessName(processInstanceEntity.getProcessName())
        .setProcessVersion(processInstanceEntity.getProcessVersion())
        .setProcessVersionTag(processInstance.getProcessVersionTag())
        .setOperations(DtoCreator.create(operations, OperationDto.class))
        .setTenantId(processInstanceEntity.getTenantId());
    if (operations != null) {
      processInstance.setHasActiveOperation(
          operations.stream()
              .anyMatch(
                  o ->
                      o.getState().equals(OperationState.SCHEDULED)
                          || o.getState().equals(OperationState.LOCKED)
                          || o.getState().equals(OperationState.SENT)));
    }
    if (processInstanceEntity.getParentProcessInstanceKey() != null) {
      processInstance.setParentInstanceId(
          String.valueOf(processInstanceEntity.getParentProcessInstanceKey()));
    }
    // convert to String[]
    if (processInstanceEntity.getSortValues() != null) {
      processInstance.setSortValues(
          SortValuesWrapper.createFrom(processInstanceEntity.getSortValues(), objectMapper));
    }

    if (processInstanceEntity.getTreePath() != null) {
      final String rootInstanceId =
          new TreePath(processInstanceEntity.getTreePath()).extractRootInstanceId();
      if (!processInstanceEntity.getId().equals(rootInstanceId)) {
        processInstance.setRootInstanceId(rootInstanceId);
      }
    }
    processInstance.setCallHierarchy(callHierarchy);
    processInstance.setPermissions(
        permissionsService.getProcessDefinitionPermissions(
            processInstanceEntity.getBpmnProcessId()));
    return processInstance;
  }

  public static List<ListViewProcessInstanceDto> createFrom(
      final List<ProcessInstanceForListViewEntity> processInstanceEntities,
      final Map<Long, List<OperationEntity>> operationsPerProcessInstance,
      final PermissionsService permissionsService,
      final ObjectMapper objectMapper) {
    if (processInstanceEntities == null) {
      return new ArrayList<>();
    }
    return processInstanceEntities.stream()
        .filter(item -> item != null)
        .map(
            item ->
                createFrom(
                    item,
                    operationsPerProcessInstance.get(item.getProcessInstanceKey()),
                    permissionsService,
                    objectMapper))
        .collect(Collectors.toList());
  }

  public String getId() {
    return id;
  }

  public ListViewProcessInstanceDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getProcessId() {
    return processId;
  }

  public ListViewProcessInstanceDto setProcessId(final String processId) {
    this.processId = processId;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public ListViewProcessInstanceDto setProcessName(final String processName) {
    this.processName = processName;
    return this;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ListViewProcessInstanceDto setProcessVersion(final Integer processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public String getProcessVersionTag() {
    return processVersionTag;
  }

  public ListViewProcessInstanceDto setProcessVersionTag(final String processVersionTag) {
    this.processVersionTag = processVersionTag;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public ListViewProcessInstanceDto setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public ListViewProcessInstanceDto setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public ProcessInstanceStateDto getState() {
    return state;
  }

  public ListViewProcessInstanceDto setState(final ProcessInstanceStateDto state) {
    this.state = state;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ListViewProcessInstanceDto setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public boolean isHasActiveOperation() {
    return hasActiveOperation;
  }

  public ListViewProcessInstanceDto setHasActiveOperation(final boolean hasActiveOperation) {
    this.hasActiveOperation = hasActiveOperation;
    return this;
  }

  public List<OperationDto> getOperations() {
    return operations;
  }

  public ListViewProcessInstanceDto setOperations(final List<OperationDto> operations) {
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

  public String getTenantId() {
    return tenantId;
  }

  public ListViewProcessInstanceDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public ListViewProcessInstanceDto setSortValues(final SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(final Set<String> permissions) {
    this.permissions = permissions;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            processId,
            processName,
            processVersion,
            processVersionTag,
            startDate,
            endDate,
            state,
            bpmnProcessId,
            hasActiveOperation,
            operations,
            parentInstanceId,
            rootInstanceId,
            callHierarchy,
            tenantId,
            permissions);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
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
    return hasActiveOperation == that.hasActiveOperation
        && Objects.equals(id, that.id)
        && Objects.equals(processId, that.processId)
        && Objects.equals(processName, that.processName)
        && Objects.equals(processVersion, that.processVersion)
        && Objects.equals(processVersionTag, that.processVersionTag)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && state == that.state
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(operations, that.operations)
        && Objects.equals(parentInstanceId, that.parentInstanceId)
        && Objects.equals(rootInstanceId, that.rootInstanceId)
        && Objects.equals(callHierarchy, that.callHierarchy)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(sortValues, that.sortValues)
        && Objects.equals(permissions, that.permissions);
  }

  @Override
  public String toString() {
    return String.format("ListViewProcessInstanceDto %s (%s)", processName, processId);
  }
}
