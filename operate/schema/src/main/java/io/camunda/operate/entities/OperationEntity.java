/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class OperationEntity extends OperateEntity<OperationEntity> {

  private Long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private Long processDefinitionKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private String bpmnProcessId;

  /** Attention! This field will be filled in only for data imported after v. 8.3.0. */
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

  public void setDecisionDefinitionKey(final Long decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
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

  public void generateId() {
    setId(UUID.randomUUID().toString());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
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
    return Objects.equals(processInstanceKey, that.processInstanceKey)
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
        && Objects.equals(migrationPlan, that.migrationPlan);
  }

  @Override
  public String toString() {
    return "OperationEntity{"
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
        + '}';
  }
}
