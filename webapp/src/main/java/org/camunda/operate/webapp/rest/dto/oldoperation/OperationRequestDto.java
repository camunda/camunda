/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.oldoperation;

import org.camunda.operate.entities.OperationType;

@Deprecated //OPE-786
public class OperationRequestDto {

  public OperationRequestDto() {
  }

  public OperationRequestDto(OperationType operationType) {
    this.operationType = operationType;
  }

  private OperationType operationType;

  /**
   * RESOLVE_INCIDENT operation.
   */
  private String incidentId;

  /**
   * UPDATE_VARIABLE operation.
   */
  private String scopeId;

  private String name;

  private String value;

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

  public String getScopeId() {
    return scopeId;
  }

  public void setScopeId(String scopeId) {
    this.scopeId = scopeId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
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
    if (incidentId != null ? !incidentId.equals(that.incidentId) : that.incidentId != null)
      return false;
    if (scopeId != null ? !scopeId.equals(that.scopeId) : that.scopeId != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    return value != null ? value.equals(that.value) : that.value == null;
  }

  @Override
  public int hashCode() {
    int result = operationType != null ? operationType.hashCode() : 0;
    result = 31 * result + (incidentId != null ? incidentId.hashCode() : 0);
    result = 31 * result + (scopeId != null ? scopeId.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }
}
