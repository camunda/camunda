/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.operation;

import org.camunda.operate.entities.OperationType;

public class OperationRequestDto {

  public OperationRequestDto() {
  }

  public OperationRequestDto(OperationType operationType) {
    this.operationType = operationType;
  }

  public OperationRequestDto(OperationType operationType, String incidentId) {
    this.operationType = operationType;
    this.incidentId = incidentId;
  }

  private OperationType operationType;

  private String incidentId;

  public OperationType getOperationType() {
    return operationType;
  }

  public void setOperationType(OperationType operationType) {
    this.operationType = operationType;
  }

  public String getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(String incidentId) {
    this.incidentId = incidentId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OperationRequestDto that = (OperationRequestDto) o;

    if (operationType != that.operationType)
      return false;
    return incidentId != null ? incidentId.equals(that.incidentId) : that.incidentId == null;
  }

  @Override
  public int hashCode() {
    int result = operationType != null ? operationType.hashCode() : 0;
    result = 31 * result + (incidentId != null ? incidentId.hashCode() : 0);
    return result;
  }
}
