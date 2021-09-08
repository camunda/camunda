/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceReferenceDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class IncidentDto {

  public static final Comparator<IncidentDto> INCIDENT_DEFAULT_COMPARATOR = (o1, o2) -> {
    if (o1.getErrorType().equals(o2.getErrorType())) {
      return o1.getId().compareTo(o2.getId());
    }
    return o1.getErrorType().compareTo(o2.getErrorType());
  };

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

  public IncidentDto setErrorType(
      final ErrorTypeDto errorType) {
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

  public IncidentDto setRootCauseInstance(
      final ProcessInstanceReferenceDto rootCauseInstance) {
    this.rootCauseInstance = rootCauseInstance;
    return this;
  }

  public static IncidentDto createFrom(IncidentEntity incidentEntity, List<OperationEntity> operations) {
    return createFrom(incidentEntity, operations, null);
  }

  public static <T> IncidentDto createFrom(final IncidentEntity incidentEntity,
      final ProcessInstanceReferenceDto rootCauseInstance) {
    return createFrom(incidentEntity, Collections.emptyList(), rootCauseInstance);
  }

  public static IncidentDto createFrom(IncidentEntity incidentEntity,
      List<OperationEntity> operations, final ProcessInstanceReferenceDto rootCauseInstance) {
    if (incidentEntity == null) {
      return null;
    }

    IncidentDto incident = new IncidentDto().setId(incidentEntity.getId())
        .setFlowNodeId(incidentEntity.getFlowNodeId())
        .setFlowNodeInstanceId(ConversionUtils.toStringOrNull(incidentEntity.getFlowNodeInstanceKey()))
        .setErrorMessage(incidentEntity.getErrorMessage())
        .setErrorType(ErrorTypeDto.createFrom(incidentEntity.getErrorType()))
        .setJobId(ConversionUtils.toStringOrNull(incidentEntity.getJobKey()))
        .setCreationTime(incidentEntity.getCreationTime());

    if (operations != null && operations.size() > 0) {
      OperationEntity lastOperation = operations.get(0); // operations are
                                                         // sorted by start date
                                                         // descendant
      incident.setLastOperation(OperationDto.createFrom(lastOperation))
          .setHasActiveOperation(operations.stream().anyMatch(
              o -> o.getState().equals(OperationState.SCHEDULED) || o.getState()
                  .equals(OperationState.LOCKED) || o.getState().equals(OperationState.SENT)));
    }

    if (rootCauseInstance != null) {
      incident.setRootCauseInstance(rootCauseInstance);
    }

    return incident;
  }


  public static List<IncidentDto> createFrom(List<IncidentEntity> incidentEntities, Map<Long, List<OperationEntity>> operations) {
    List<IncidentDto> result = new ArrayList<>();
    if (incidentEntities != null) {
      for (IncidentEntity incidentEntity : incidentEntities) {
        if (incidentEntity != null) {
          result.add(createFrom(incidentEntity, operations.get(incidentEntity.getKey())));
        }
      }
    }
    return result;
  }

  public static List<IncidentDto> sortDefault(List<IncidentDto> incidents) {
    Collections.sort(incidents, INCIDENT_DEFAULT_COMPARATOR);
    return incidents;
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
    return hasActiveOperation == that.hasActiveOperation &&
        Objects.equals(id, that.id) &&
        Objects.equals(errorType, that.errorType) &&
        Objects.equals(errorMessage, that.errorMessage) &&
        Objects.equals(flowNodeId, that.flowNodeId) &&
        Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId) &&
        Objects.equals(jobId, that.jobId) &&
        Objects.equals(creationTime, that.creationTime) &&
        Objects.equals(lastOperation, that.lastOperation) &&
        Objects.equals(rootCauseInstance, that.rootCauseInstance);
  }

  @Override
  public int hashCode() {
    return Objects
        .hash(id, errorType, errorMessage, flowNodeId, flowNodeInstanceId, jobId, creationTime,
            hasActiveOperation, lastOperation, rootCauseInstance);
  }

}
