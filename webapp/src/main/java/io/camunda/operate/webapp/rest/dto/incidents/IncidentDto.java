/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.util.ConversionUtils;

public class IncidentDto {

  public static final Comparator<IncidentDto> INCIDENT_DEFAULT_COMPARATOR = (o1, o2) -> {
    if (o1.getErrorType().equals(o2.getErrorType())) {
      return o1.getId().compareTo(o2.getId());
    }
    return o1.getErrorType().compareTo(o2.getErrorType());
  };
  private String id;

  private String errorType;

  private String errorMessage;

  private String flowNodeId;

  private String flowNodeInstanceId;

  private String jobId;

  private OffsetDateTime creationTime;

  private boolean hasActiveOperation = false;

  private OperationDto lastOperation;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getErrorType() {
    return errorType;
  }

  public void setErrorType(String errorType) {
    this.errorType = errorType;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public void setFlowNodeInstanceId(String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
  }

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
  }

  public boolean isHasActiveOperation() {
    return hasActiveOperation;
  }

  public void setHasActiveOperation(boolean hasActiveOperation) {
    this.hasActiveOperation = hasActiveOperation;
  }

  public OperationDto getLastOperation() {
    return lastOperation;
  }

  public void setLastOperation(OperationDto lastOperation) {
    this.lastOperation = lastOperation;
  }

  public static IncidentDto createFrom(IncidentEntity incidentEntity, List<OperationEntity> operations) {
    if (incidentEntity == null) {
      return null;
    }

    IncidentDto incident = new IncidentDto();
    incident.setId(incidentEntity.getId());
    incident.setFlowNodeId(incidentEntity.getFlowNodeId());
    incident.setFlowNodeInstanceId(ConversionUtils.toStringOrNull(incidentEntity.getFlowNodeInstanceKey()));
    incident.setErrorMessage(incidentEntity.getErrorMessage());
    incident.setErrorType(incidentEntity.getErrorType().getTitle());
    incident.setJobId(ConversionUtils.toStringOrNull(incidentEntity.getJobKey()));
    incident.setCreationTime(incidentEntity.getCreationTime());

    if (operations != null && operations.size() > 0) {
      OperationEntity lastOperation = operations.get(0); // operations are
                                                         // sorted by start date
                                                         // descendant
      incident.setLastOperation(OperationDto.createFrom(lastOperation));

      incident.setHasActiveOperation(operations.stream().anyMatch(
          o -> o.getState().equals(OperationState.SCHEDULED) || o.getState().equals(OperationState.LOCKED) || o.getState().equals(OperationState.SENT)));
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
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentDto that = (IncidentDto) o;

    if (hasActiveOperation != that.hasActiveOperation)
      return false;
    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (errorType != null ? !errorType.equals(that.errorType) : that.errorType != null)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    if (flowNodeId != null ? !flowNodeId.equals(that.flowNodeId) : that.flowNodeId != null)
      return false;
    if (flowNodeInstanceId != null ? !flowNodeInstanceId.equals(that.flowNodeInstanceId) : that.flowNodeInstanceId != null)
      return false;
    if (jobId != null ? !jobId.equals(that.jobId) : that.jobId != null)
      return false;
    if (creationTime != null ? !creationTime.equals(that.creationTime) : that.creationTime != null)
      return false;
    return lastOperation != null ? lastOperation.equals(that.lastOperation) : that.lastOperation == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (errorType != null ? errorType.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (flowNodeId != null ? flowNodeId.hashCode() : 0);
    result = 31 * result + (flowNodeInstanceId != null ? flowNodeInstanceId.hashCode() : 0);
    result = 31 * result + (jobId != null ? jobId.hashCode() : 0);
    result = 31 * result + (creationTime != null ? creationTime.hashCode() : 0);
    result = 31 * result + (hasActiveOperation ? 1 : 0);
    result = 31 * result + (lastOperation != null ? lastOperation.hashCode() : 0);
    return result;
  }
}
