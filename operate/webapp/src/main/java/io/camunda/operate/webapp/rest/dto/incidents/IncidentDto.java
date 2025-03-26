/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.webapp.data.IncidentDataHolder;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceReferenceDto;
import io.camunda.operate.webapp.rest.dto.metadata.DecisionInstanceReferenceDto;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class IncidentDto {

  public static final Comparator<IncidentDto> INCIDENT_DEFAULT_COMPARATOR =
      (o1, o2) -> {
        if (o1.getErrorType().equals(o2.getErrorType())) {
          return o1.getId().compareTo(o2.getId());
        }
        return o1.getErrorType().compareTo(o2.getErrorType());
      };

  public static final String FALLBACK_PROCESS_DEFINITION_NAME = "Unknown process";

  private String id;

  private ErrorTypeDto errorType;

  private String errorMessage;

  private String flowNodeId;

  private String flowNodeInstanceId;

  private String jobId;

  private OffsetDateTime creationTime;

  private boolean hasActiveOperation = false;

  private OperationDto lastOperation;

  private ProcessInstanceReferenceDto rootCauseInstance;

  private DecisionInstanceReferenceDto rootCauseDecision;

  public static <T> IncidentDto createFrom(
      final IncidentEntity incidentEntity,
      final Map<Long, String> processNames,
      final IncidentDataHolder incidentData,
      final DecisionInstanceReferenceDto rootCauseDecision) {
    return createFrom(
        incidentEntity, Collections.emptyList(), processNames, incidentData, rootCauseDecision);
  }

  public static IncidentDto createFrom(
      final IncidentEntity incidentEntity,
      final List<OperationEntity> operations,
      final Map<Long, String> processNames,
      final IncidentDataHolder incidentData,
      final DecisionInstanceReferenceDto rootCauseDecision) {
    if (incidentEntity == null) {
      return null;
    }

    final IncidentDto incident =
        new IncidentDto()
            .setId(incidentEntity.getId())
            .setFlowNodeId(incidentEntity.getFlowNodeId())
            .setFlowNodeInstanceId(
                ConversionUtils.toStringOrNull(incidentEntity.getFlowNodeInstanceKey()))
            .setErrorMessage(incidentEntity.getErrorMessage())
            .setErrorType(ErrorTypeDto.createFrom(incidentEntity.getErrorType()))
            .setJobId(ConversionUtils.toStringOrNull(incidentEntity.getJobKey()))
            .setCreationTime(incidentEntity.getCreationTime());

    if (operations != null && operations.size() > 0) {
      final OperationEntity lastOperation = operations.get(0); // operations are
      // sorted by start date
      // descendant
      incident
          .setLastOperation(DtoCreator.create(lastOperation, OperationDto.class))
          .setHasActiveOperation(
              operations.stream()
                  .anyMatch(
                      o ->
                          o.getState().equals(OperationState.SCHEDULED)
                              || o.getState().equals(OperationState.LOCKED)
                              || o.getState().equals(OperationState.SENT)));
    }

    // do not return root cause when it's a "local" incident
    if (incidentData != null
        && incident.getFlowNodeInstanceId() != incidentData.getFinalFlowNodeInstanceId()) {
      incident.setFlowNodeId(incidentData.getFinalFlowNodeId());
      incident.setFlowNodeInstanceId(incidentData.getFinalFlowNodeInstanceId());

      final ProcessInstanceReferenceDto rootCauseInstance =
          new ProcessInstanceReferenceDto()
              .setInstanceId(String.valueOf(incidentEntity.getProcessInstanceKey()))
              .setProcessDefinitionId(String.valueOf(incidentEntity.getProcessDefinitionKey()));
      if (processNames != null
          && processNames.get(incidentEntity.getProcessDefinitionKey()) != null) {
        rootCauseInstance.setProcessDefinitionName(
            processNames.get(incidentEntity.getProcessDefinitionKey()));
      } else {
        rootCauseInstance.setProcessDefinitionName(FALLBACK_PROCESS_DEFINITION_NAME);
      }
      incident.setRootCauseInstance(rootCauseInstance);
    }

    if (rootCauseDecision != null) {
      incident.setRootCauseDecision(rootCauseDecision);
    }

    return incident;
  }

  public static List<IncidentDto> createFrom(
      final List<IncidentEntity> incidentEntities,
      final Map<Long, List<OperationEntity>> operations,
      final Map<Long, String> processNames,
      final Map<String, IncidentDataHolder> incidentData) {
    if (incidentEntities != null) {
      return incidentEntities.stream()
          .filter(inc -> inc != null)
          .map(
              inc ->
                  createFrom(
                      inc,
                      operations.get(inc.getKey()),
                      processNames,
                      incidentData.get(inc.getId()),
                      null))
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  public static List<IncidentDto> sortDefault(final List<IncidentDto> incidents) {
    Collections.sort(incidents, INCIDENT_DEFAULT_COMPARATOR);
    return incidents;
  }

  public String getId() {
    return id;
  }

  public IncidentDto setId(final String id) {
    this.id = id;
    return this;
  }

  public ErrorTypeDto getErrorType() {
    return errorType;
  }

  public IncidentDto setErrorType(final ErrorTypeDto errorType) {
    this.errorType = errorType;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public IncidentDto setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public IncidentDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public IncidentDto setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getJobId() {
    return jobId;
  }

  public IncidentDto setJobId(final String jobId) {
    this.jobId = jobId;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public IncidentDto setCreationTime(final OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public boolean isHasActiveOperation() {
    return hasActiveOperation;
  }

  public IncidentDto setHasActiveOperation(final boolean hasActiveOperation) {
    this.hasActiveOperation = hasActiveOperation;
    return this;
  }

  public OperationDto getLastOperation() {
    return lastOperation;
  }

  public IncidentDto setLastOperation(final OperationDto lastOperation) {
    this.lastOperation = lastOperation;
    return this;
  }

  public ProcessInstanceReferenceDto getRootCauseInstance() {
    return rootCauseInstance;
  }

  public IncidentDto setRootCauseInstance(final ProcessInstanceReferenceDto rootCauseInstance) {
    this.rootCauseInstance = rootCauseInstance;
    return this;
  }

  public DecisionInstanceReferenceDto getRootCauseDecision() {
    return rootCauseDecision;
  }

  public IncidentDto setRootCauseDecision(final DecisionInstanceReferenceDto rootCauseDecision) {
    this.rootCauseDecision = rootCauseDecision;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        errorType,
        errorMessage,
        flowNodeId,
        flowNodeInstanceId,
        jobId,
        creationTime,
        hasActiveOperation,
        lastOperation,
        rootCauseInstance,
        rootCauseDecision);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncidentDto that = (IncidentDto) o;
    return hasActiveOperation == that.hasActiveOperation
        && Objects.equals(id, that.id)
        && Objects.equals(errorType, that.errorType)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && Objects.equals(jobId, that.jobId)
        && Objects.equals(creationTime, that.creationTime)
        && Objects.equals(lastOperation, that.lastOperation)
        && Objects.equals(rootCauseInstance, that.rootCauseInstance)
        && Objects.equals(rootCauseDecision, that.rootCauseDecision);
  }

  @Override
  public String toString() {
    return "IncidentDto{"
        + "id='"
        + id
        + '\''
        + ", errorType="
        + errorType
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", flowNodeId='"
        + flowNodeId
        + '\''
        + ", flowNodeInstanceId='"
        + flowNodeInstanceId
        + '\''
        + ", jobId='"
        + jobId
        + '\''
        + ", creationTime="
        + creationTime
        + ", hasActiveOperation="
        + hasActiveOperation
        + ", lastOperation="
        + lastOperation
        + ", rootCauseInstance="
        + rootCauseInstance
        + ", rootCauseDecision="
        + rootCauseDecision
        + '}';
  }
}
