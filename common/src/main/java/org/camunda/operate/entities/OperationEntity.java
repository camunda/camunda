/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

public class OperationEntity extends OperateEntity<OperationEntity> {

  private Long workflowInstanceKey;
  private Long incidentKey;
  private Long scopeKey;
  private String variableName;
  private String variableValue;
  private OperationType type;
  @Deprecated //OPE-786
  private OffsetDateTime startDate;
  @Deprecated //OPE-786
  private OffsetDateTime endDate;
  private OffsetDateTime lockExpirationTime;
  private String lockOwner;
  private OperationState state;
  private String errorMessage;
  private String batchOperationId;
  private Long zeebeCommandKey;
  private String username;

  public Long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public void setWorkflowInstanceKey(Long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public Long getIncidentKey() {
    return incidentKey;
  }

  public void setIncidentKey(Long incidentKey) {
    this.incidentKey = incidentKey;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public void setScopeKey(Long scopeKey) {
    this.scopeKey = scopeKey;
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

  public OperationType getType() {
    return type;
  }

  public void setType(OperationType type) {
    this.type = type;
  }

  public Long getZeebeCommandKey() {
    return zeebeCommandKey;
  }

  public void setZeebeCommandKey(Long zeebeCommandKey) {
    this.zeebeCommandKey = zeebeCommandKey;
  }

  @Deprecated //OPE-786
  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  @Deprecated //OPE-786
  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public OperationState getState() {
    return state;
  }

  public void setState(OperationState state) {
    this.state = state;
  }

  public OffsetDateTime getLockExpirationTime() {
    return lockExpirationTime;
  }

  public void setLockExpirationTime(OffsetDateTime lockExpirationTime) {
    this.lockExpirationTime = lockExpirationTime;
  }

  public String getLockOwner() {
    return lockOwner;
  }

  public void setLockOwner(String lockOwner) {
    this.lockOwner = lockOwner;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getBatchOperationId() {
    return batchOperationId;
  }

  public void setBatchOperationId(String batchOperationId) {
    this.batchOperationId = batchOperationId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void generateId() {
    setId(UUID.randomUUID().toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    OperationEntity that = (OperationEntity) o;

    if (workflowInstanceKey != null ? !workflowInstanceKey.equals(that.workflowInstanceKey) : that.workflowInstanceKey != null)
      return false;
    if (incidentKey != null ? !incidentKey.equals(that.incidentKey) : that.incidentKey != null)
      return false;
    if (scopeKey != null ? !scopeKey.equals(that.scopeKey) : that.scopeKey != null)
      return false;
    if (variableName != null ? !variableName.equals(that.variableName) : that.variableName != null)
      return false;
    if (variableValue != null ? !variableValue.equals(that.variableValue) : that.variableValue != null)
      return false;
    if (type != that.type)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (lockExpirationTime != null ? !lockExpirationTime.equals(that.lockExpirationTime) : that.lockExpirationTime != null)
      return false;
    if (lockOwner != null ? !lockOwner.equals(that.lockOwner) : that.lockOwner != null)
      return false;
    if (state != that.state)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    if (batchOperationId != null ? !batchOperationId.equals(that.batchOperationId) : that.batchOperationId != null)
      return false;
    if (zeebeCommandKey != null ? !zeebeCommandKey.equals(that.zeebeCommandKey) : that.zeebeCommandKey != null)
      return false;
    return username != null ? username.equals(that.username) : that.username == null;

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (workflowInstanceKey != null ? workflowInstanceKey.hashCode() : 0);
    result = 31 * result + (incidentKey != null ? incidentKey.hashCode() : 0);
    result = 31 * result + (scopeKey != null ? scopeKey.hashCode() : 0);
    result = 31 * result + (variableName != null ? variableName.hashCode() : 0);
    result = 31 * result + (variableValue != null ? variableValue.hashCode() : 0);
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (lockExpirationTime != null ? lockExpirationTime.hashCode() : 0);
    result = 31 * result + (lockOwner != null ? lockOwner.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (batchOperationId != null ? batchOperationId.hashCode() : 0);
    result = 31 * result + (zeebeCommandKey != null ? zeebeCommandKey.hashCode() : 0);
    result = 31 * result + (username != null ? username.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "OperationEntity{" + "workflowInstanceKey=" + workflowInstanceKey + ", incidentKey=" + incidentKey + ", scopeKey=" + scopeKey + ", variableName='"
        + variableName + '\'' + ", variableValue='" + variableValue + '\'' + ", type=" + type + ", startDate=" + startDate
        + ", endDate=" + endDate + ", lockExpirationTime=" + lockExpirationTime + ", lockOwner='" + lockOwner + '\'' + ", state=" + state + ", errorMessage='"
        + errorMessage + '\'' + ", batchOperationId='" + batchOperationId + '\'' + ", zeebeCommandKey=" + zeebeCommandKey + '}';
  }
}
