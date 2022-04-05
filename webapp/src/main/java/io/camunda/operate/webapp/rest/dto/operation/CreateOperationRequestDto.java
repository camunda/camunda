/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import io.camunda.operate.entities.OperationType;

public class CreateOperationRequestDto {

  public CreateOperationRequestDto() {
  }

  public CreateOperationRequestDto(OperationType operationType) {
    this.operationType = operationType;
  }

  private OperationType operationType;

  /**
   * Batch operation name.
   */
  private String name;

  /**
   * RESOLVE_INCIDENT operation.
   */
  private String incidentId;

  /**
   * UPDATE_VARIABLE operation.
   */
  private String variableScopeId;

  private String variableName;

  private String variableValue;

  public OperationType getOperationType() {
    return operationType;
  }

  public CreateOperationRequestDto setOperationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(String incidentId) {
    this.incidentId = incidentId;
  }

  public String getVariableScopeId() {
    return variableScopeId;
  }

  public void setVariableScopeId(String variableScopeId) {
    this.variableScopeId = variableScopeId;
  }

  public String getVariableName() {
    return variableName;
  }

  public void setVariableName(String variableName) {
    this.variableName = variableName;
  }

  public String getVariableValue() {
    return variableValue;
  }

  public void setVariableValue(String variableValue) {
    this.variableValue = variableValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    CreateOperationRequestDto that = (CreateOperationRequestDto) o;

    if (operationType != that.operationType)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (incidentId != null ? !incidentId.equals(that.incidentId) : that.incidentId != null)
      return false;
    if (variableScopeId != null ? !variableScopeId.equals(that.variableScopeId) : that.variableScopeId != null)
      return false;
    if (variableName != null ? !variableName.equals(that.variableName) : that.variableName != null)
      return false;
    return variableValue != null ? variableValue.equals(that.variableValue) : that.variableValue == null;

  }

  @Override
  public int hashCode() {
    int result = operationType != null ? operationType.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (incidentId != null ? incidentId.hashCode() : 0);
    result = 31 * result + (variableScopeId != null ? variableScopeId.hashCode() : 0);
    result = 31 * result + (variableName != null ? variableName.hashCode() : 0);
    result = 31 * result + (variableValue != null ? variableValue.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "CreateOperationRequestDto{" + "operationType=" + operationType + ", name='" + name + '\'' + ", incidentId='" + incidentId + '\''
        + ", variableScopeId='" + variableScopeId + '\'' + ", variableName='" + variableName + '\'' + ", variableValue='" + variableValue + '\'' + '}';
  }
}
