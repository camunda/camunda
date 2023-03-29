/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class OperationEntity extends OperateEntity<OperationEntity> {

  private Long processInstanceKey;
  /**
   * Attention! This field will be filled in only for data imported after v. 8.2.0.
   */
  private Long processDefinitionKey;
  /**
   * Attention! This field will be filled in only for data imported after v. 8.2.0.
   */
  private String bpmnProcessId;
  private Long incidentKey;
  private Long scopeKey;
  private String variableName;
  private String variableValue;
  private OperationType type;
  private OffsetDateTime lockExpirationTime;
  private String lockOwner;
  private OperationState state;
  private String errorMessage;
  private String batchOperationId;
  private Long zeebeCommandKey;
  private String username;
  private String modifyInstructions;

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
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

  public String getModifyInstructions() {
    return modifyInstructions;
  }

  public OperationEntity setModifyInstructions(String modifyInstructions) {
    this.modifyInstructions = modifyInstructions;
    return this;
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
    return Objects.equals(processInstanceKey, that.processInstanceKey) && Objects.equals(processDefinitionKey, that.processDefinitionKey) && Objects.equals(
        bpmnProcessId, that.bpmnProcessId) && Objects.equals(incidentKey, that.incidentKey) && Objects.equals(scopeKey, that.scopeKey) && Objects.equals(
        variableName, that.variableName) && Objects.equals(variableValue, that.variableValue) && type == that.type && Objects.equals(lockExpirationTime,
        that.lockExpirationTime) && Objects.equals(lockOwner, that.lockOwner) && state == that.state && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(batchOperationId, that.batchOperationId) && Objects.equals(zeebeCommandKey, that.zeebeCommandKey) && Objects.equals(username,
        that.username) && Objects.equals(modifyInstructions, that.modifyInstructions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), processInstanceKey, processDefinitionKey, bpmnProcessId, incidentKey, scopeKey, variableName, variableValue, type,
        lockExpirationTime, lockOwner, state, errorMessage, batchOperationId, zeebeCommandKey, username, modifyInstructions);
  }

  @Override
  public String toString() {
    return "OperationEntity{" + "processInstanceKey=" + processInstanceKey + ", processDefinitionKey=" + processDefinitionKey + ", bpmnProcessId='"
        + bpmnProcessId + '\'' + ", incidentKey=" + incidentKey + ", scopeKey=" + scopeKey + ", variableName='" + variableName + '\'' + ", variableValue='"
        + variableValue + '\'' + ", type=" + type + ", lockExpirationTime=" + lockExpirationTime + ", lockOwner='" + lockOwner + '\'' + ", state=" + state
        + ", errorMessage='" + errorMessage + '\'' + ", batchOperationId='" + batchOperationId + '\'' + ", zeebeCommandKey=" + zeebeCommandKey + ", username='"
        + username + '\'' + ", modifyInstructions='" + modifyInstructions + '\'' + '}';
  }
}
