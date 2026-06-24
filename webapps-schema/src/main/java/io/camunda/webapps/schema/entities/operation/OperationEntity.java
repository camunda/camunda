/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operation;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.SinceVersion;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class OperationEntity extends AbstractExporterEntity<OperationEntity> {

  /**
   * Is used by batch operation engine in zeebe to identify the resource (process, incident, ...)
   */
  @BeforeVersion880 private Long itemKey;

  @BeforeVersion880 private Long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  @BeforeVersion880 private Long processDefinitionKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  @BeforeVersion880 private String bpmnProcessId;

  /** Attention! This field will be filled in only for data imported after v. 8.3.0. */
  @BeforeVersion880 private Long decisionDefinitionKey;

  @BeforeVersion880 private Long incidentKey;
  @BeforeVersion880 private Long scopeKey;
  @BeforeVersion880 private String variableName;
  @BeforeVersion880 private String variableValue;
  @BeforeVersion880 private OperationType type;
  @BeforeVersion880 private OffsetDateTime lockExpirationTime;
  @BeforeVersion880 private String lockOwner;
  @BeforeVersion880 private OperationState state;
  @BeforeVersion880 private String errorMessage;
  @BeforeVersion880 private String batchOperationId;
  @BeforeVersion880 private Long zeebeCommandKey;
  @BeforeVersion880 private String username;
  @BeforeVersion880 private String modifyInstructions;
  @BeforeVersion880 private String migrationPlan;

  @BeforeVersion880 private OffsetDateTime completedDate;

  /** Attention! This field will be filled in only for data imported after v. 8.9.0. */
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long rootProcessInstanceKey;

  public Long getItemKey() {
    return itemKey;
  }

  public OperationEntity setItemKey(final Long itemKey) {
    this.itemKey = itemKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public OperationEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public OperationEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public OperationEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Long getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public OperationEntity setDecisionDefinitionKey(final Long decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
    return this;
  }

  public Long getIncidentKey() {
    return incidentKey;
  }

  public OperationEntity setIncidentKey(final Long incidentKey) {
    this.incidentKey = incidentKey;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public OperationEntity setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public String getVariableName() {
    return variableName;
  }

  public OperationEntity setVariableName(final String variableName) {
    this.variableName = variableName;
    return this;
  }

  public String getVariableValue() {
    return variableValue;
  }

  public OperationEntity setVariableValue(final String variableValue) {
    this.variableValue = variableValue;
    return this;
  }

  public OperationType getType() {
    return type;
  }

  public OperationEntity setType(final OperationType type) {
    this.type = type;
    return this;
  }

  public Long getZeebeCommandKey() {
    return zeebeCommandKey;
  }

  public OperationEntity setZeebeCommandKey(final Long zeebeCommandKey) {
    this.zeebeCommandKey = zeebeCommandKey;
    return this;
  }

  public OperationState getState() {
    return state;
  }

  public OperationEntity setState(final OperationState state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getLockExpirationTime() {
    return lockExpirationTime;
  }

  public OperationEntity setLockExpirationTime(final OffsetDateTime lockExpirationTime) {
    this.lockExpirationTime = lockExpirationTime;
    return this;
  }

  public String getLockOwner() {
    return lockOwner;
  }

  public OperationEntity setLockOwner(final String lockOwner) {
    this.lockOwner = lockOwner;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public OperationEntity setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getBatchOperationId() {
    return batchOperationId;
  }

  public OperationEntity setBatchOperationId(final String batchOperationId) {
    this.batchOperationId = batchOperationId;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public OperationEntity setUsername(final String username) {
    this.username = username;
    return this;
  }

  public String getModifyInstructions() {
    return modifyInstructions;
  }

  public OperationEntity setModifyInstructions(final String modifyInstructions) {
    this.modifyInstructions = modifyInstructions;
    return this;
  }

  public String getMigrationPlan() {
    return migrationPlan;
  }

  public OperationEntity setMigrationPlan(final String migrationPlan) {
    this.migrationPlan = migrationPlan;
    return this;
  }

  public OffsetDateTime getCompletedDate() {
    return completedDate;
  }

  public OperationEntity setCompletedDate(final OffsetDateTime completedDate) {
    this.completedDate = completedDate;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public OperationEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public OperationEntity withGeneratedId() {
    // Operation reference has to be positive and `UUID.randomUUID().getMostSignificantBits()` can
    // generate negative values
    final long operationReference = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    setId(String.valueOf(operationReference));
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        itemKey,
        processInstanceKey,
        processDefinitionKey,
        bpmnProcessId,
        decisionDefinitionKey,
        incidentKey,
        scopeKey,
        variableName,
        variableValue,
        type,
        lockExpirationTime,
        lockOwner,
        state,
        errorMessage,
        batchOperationId,
        zeebeCommandKey,
        username,
        modifyInstructions,
        migrationPlan);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final OperationEntity that = (OperationEntity) o;
    return Objects.equals(itemKey, that.itemKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(decisionDefinitionKey, that.decisionDefinitionKey)
        && Objects.equals(incidentKey, that.incidentKey)
        && Objects.equals(scopeKey, that.scopeKey)
        && Objects.equals(variableName, that.variableName)
        && Objects.equals(variableValue, that.variableValue)
        && type == that.type
        && Objects.equals(lockExpirationTime, that.lockExpirationTime)
        && Objects.equals(lockOwner, that.lockOwner)
        && state == that.state
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(batchOperationId, that.batchOperationId)
        && Objects.equals(zeebeCommandKey, that.zeebeCommandKey)
        && Objects.equals(username, that.username)
        && Objects.equals(modifyInstructions, that.modifyInstructions)
        && Objects.equals(migrationPlan, that.migrationPlan)
        && Objects.equals(rootProcessInstanceKey, that.rootProcessInstanceKey);
  }

  @Override
  public String toString() {
    return "OperationEntity{"
        + "itemKey="
        + itemKey
        + "processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", decisionDefinitionKey="
        + decisionDefinitionKey
        + ", incidentKey="
        + incidentKey
        + ", scopeKey="
        + scopeKey
        + ", variableName='"
        + variableName
        + '\''
        + ", variableValue='"
        + variableValue
        + '\''
        + ", type="
        + type
        + ", lockExpirationTime="
        + lockExpirationTime
        + ", lockOwner='"
        + lockOwner
        + '\''
        + ", state="
        + state
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", batchOperationId='"
        + batchOperationId
        + '\''
        + ", zeebeCommandKey="
        + zeebeCommandKey
        + ", username='"
        + username
        + '\''
        + ", modifyInstructions='"
        + modifyInstructions
        + '\''
        + ", migrationPlan='"
        + migrationPlan
        + '\''
        + ", rootProcessInstanceKey="
        + rootProcessInstanceKey
        + '}';
  }
}
