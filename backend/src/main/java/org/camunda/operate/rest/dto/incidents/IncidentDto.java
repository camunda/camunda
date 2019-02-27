/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.incidents;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.rest.dto.OperationDto;

public class IncidentDto {

  private String id;

  private String errorType;

  private String errorMessage;

  private String flowNodeId;

  private String flowNodeInstanceId;

  private Long jobKey;

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

  public Long getJobKey() {
    return jobKey;
  }

  public void setJobKey(Long jobKey) {
    this.jobKey = jobKey;
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

  public static IncidentDto createFrom(IncidentEntity incidentEntity) {
    if (incidentEntity == null) {
      return null;
    }
    IncidentDto incident = new IncidentDto();
    incident.setId(incidentEntity.getId());
    incident.setFlowNodeId(incidentEntity.getFlowNodeId());
    incident.setFlowNodeInstanceId(incidentEntity.getFlowNodeInstanceId());
    incident.setErrorMessage(incidentEntity.getErrorMessage());
    incident.setErrorType(incidentEntity.getErrorType().getTitle());
    incident.setJobKey(incidentEntity.getJobKey());
    incident.setCreationTime(incidentEntity.getCreationTime());
    //TODO operations
    return incident;
  }

  public static List<IncidentDto> createFrom(List<IncidentEntity> incidentEntities) {
    List<IncidentDto> result = new ArrayList<>();
    if (incidentEntities != null) {
      for (IncidentEntity incidentEntity: incidentEntities) {
        if (incidentEntity != null) {
          result.add(createFrom(incidentEntity));
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
    if (jobKey != null ? !jobKey.equals(that.jobKey) : that.jobKey != null)
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
    result = 31 * result + (jobKey != null ? jobKey.hashCode() : 0);
    result = 31 * result + (creationTime != null ? creationTime.hashCode() : 0);
    result = 31 * result + (hasActiveOperation ? 1 : 0);
    result = 31 * result + (lastOperation != null ? lastOperation.hashCode() : 0);
    return result;
  }
}
