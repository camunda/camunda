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
  /**
   * Attention! This field will be filled in only for data imported after v. 8.3.0.
   */
  private Long decisionDefinitionKey;
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
  private String migrationPlan;

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public OperationEntity setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public OperationEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Long getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public void setDecisionDefinitionKey(Long decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public Long getIncidentKey() {
    return incidentKey;
  }

  public OperationEntity setIncidentKey(Long incidentKey) {
    this.incidentKey = incidentKey;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public OperationEntity setScopeKey(Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public String getVariableName() {
    return variableName;
  }

  public OperationEntity setVariableName(String variableName) {
    this.variableName = variableName;
    return this;
  }

  public String getVariableValue() {
    return variableValue;
  }

  public OperationEntity setVariableValue(String variableValue) {
    this.variableValue = variableValue;
    return this;
  }

  public OperationType getType() {
    return type;
  }

  public OperationEntity setType(OperationType type) {
    this.type = type;
    return this;
  }

  public Long getZeebeCommandKey() {
    return zeebeCommandKey;
  }

  public OperationEntity setZeebeCommandKey(Long zeebeCommandKey) {
    this.zeebeCommandKey = zeebeCommandKey;
    return this;
  }

  public OperationState getState() {
    return state;
  }

  public OperationEntity setState(OperationState state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getLockExpirationTime() {
    return lockExpirationTime;
  }

  public OperationEntity setLockExpirationTime(OffsetDateTime lockExpirationTime) {
    this.lockExpirationTime = lockExpirationTime;
    return this;
  }

  public String getLockOwner() {
    return lockOwner;
  }

  public OperationEntity setLockOwner(String lockOwner) {
    this.lockOwner = lockOwner;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public OperationEntity setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getBatchOperationId() {
    return batchOperationId;
  }

  public OperationEntity setBatchOperationId(String batchOperationId) {
    this.batchOperationId = batchOperationId;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public OperationEntity setUsername(String username) {
    this.username = username;
    return this;
  }

  public String getModifyInstructions() {
    return modifyInstructions;
  }

  public OperationEntity setModifyInstructions(String modifyInstructions) {
    this.modifyInstructions = modifyInstructions;
    return this;
  }

  public String getMigrationPlan() {
    return migrationPlan;
  }

  public OperationEntity setMigrationPlan(String migrationPlan) {
    this.migrationPlan = migrationPlan;
    return this;
  }

  public void generateId() {
    setId(UUID.randomUUID().toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    OperationEntity that = (OperationEntity) o;
    return Objects.equals(processInstanceKey, that.processInstanceKey) && Objects.equals(processDefinitionKey, that.processDefinitionKey) &&
        Objects.equals(bpmnProcessId, that.bpmnProcessId) && Objects.equals(decisionDefinitionKey, that.decisionDefinitionKey) &&
        Objects.equals(incidentKey, that.incidentKey) && Objects.equals(scopeKey, that.scopeKey) && Objects.equals(variableName, that.variableName) &&
        Objects.equals(variableValue, that.variableValue) && type == that.type && Objects.equals(lockExpirationTime, that.lockExpirationTime) &&
        Objects.equals(lockOwner, that.lockOwner) && state == that.state && Objects.equals(errorMessage, that.errorMessage) &&
        Objects.equals(batchOperationId, that.batchOperationId) && Objects.equals(zeebeCommandKey, that.zeebeCommandKey) &&
        Objects.equals(username, that.username) && Objects.equals(modifyInstructions, that.modifyInstructions) &&
        Objects.equals(migrationPlan, that.migrationPlan);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), processInstanceKey, processDefinitionKey, bpmnProcessId, decisionDefinitionKey, incidentKey, scopeKey,
        variableName, variableValue, type, lockExpirationTime, lockOwner, state, errorMessage, batchOperationId, zeebeCommandKey, username,
        modifyInstructions, migrationPlan);
  }

  @Override
  public String toString() {
    return "OperationEntity{" +
        "processInstanceKey=" + processInstanceKey +
        ", processDefinitionKey=" + processDefinitionKey +
        ", bpmnProcessId='" + bpmnProcessId + '\'' +
        ", decisionDefinitionKey=" + decisionDefinitionKey +
        ", incidentKey=" + incidentKey +
        ", scopeKey=" + scopeKey +
        ", variableName='" + variableName + '\'' +
        ", variableValue='" + variableValue + '\'' +
        ", type=" + type +
        ", lockExpirationTime=" + lockExpirationTime +
        ", lockOwner='" + lockOwner + '\'' +
        ", state=" + state +
        ", errorMessage='" + errorMessage + '\'' +
        ", batchOperationId='" + batchOperationId + '\'' +
        ", zeebeCommandKey=" + zeebeCommandKey +
        ", username='" + username + '\'' +
        ", modifyInstructions='" + modifyInstructions + '\'' +
        ", migrationPlan='" + migrationPlan + '\'' +
        '}';
  }
}
