/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.OffsetDateTime;
import java.util.Objects;

public class IncidentEntity extends OperateZeebeEntity<IncidentEntity> {

  private ErrorType errorType;

  private String errorMessage;

  // Is only used by binding to ES results
  private Integer errorMessageHash;

  private IncidentState state;

  private String flowNodeId;

  private Long flowNodeInstanceKey;

  private Long jobKey;

  private Long processInstanceKey;

  private OffsetDateTime creationTime;

  private Long processDefinitionKey;

  private String bpmnProcessId;

  private String treePath;

  @Deprecated
  @JsonIgnore
  private boolean pending = true;

  public ErrorType getErrorType() {
    return errorType;
  }

  public IncidentEntity setErrorType(ErrorType errorType) {
    this.errorType = errorType;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public IncidentEntity setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
    setErrorMessageHash(errorMessage.hashCode());
    return this;
  }

  public void setErrorMessageHash(Integer errorMessageHash) {
    this.errorMessageHash = errorMessageHash;
  }

  public Integer getErrorMessageHash() {
    return errorMessage.hashCode();
  }

  public IncidentState getState() {
    return state;
  }

  public IncidentEntity setState(IncidentState state) {
    this.state = state;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public IncidentEntity setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public IncidentEntity setFlowNodeInstanceKey(Long flowNodeInstanceId) {
    this.flowNodeInstanceKey = flowNodeInstanceId;
    return this;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public IncidentEntity setJobKey(Long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public IncidentEntity setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public IncidentEntity setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public IncidentEntity setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public IncidentEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public IncidentEntity setTreePath(final String treePath) {
    this.treePath = treePath;
    return this;
  }

  public boolean isPending() {
    return pending;
  }

  public IncidentEntity setPending(final boolean pending) {
    this.pending = pending;
    return this;
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
    final IncidentEntity that = (IncidentEntity) o;
    return pending == that.pending &&
        errorType == that.errorType &&
        Objects.equals(errorMessage, that.errorMessage) &&
        Objects.equals(errorMessageHash, that.errorMessageHash) &&
        state == that.state &&
        Objects.equals(flowNodeId, that.flowNodeId) &&
        Objects.equals(flowNodeInstanceKey, that.flowNodeInstanceKey) &&
        Objects.equals(jobKey, that.jobKey) &&
        Objects.equals(processInstanceKey, that.processInstanceKey) &&
        Objects.equals(creationTime, that.creationTime) &&
        Objects.equals(processDefinitionKey, that.processDefinitionKey) &&
        Objects.equals(bpmnProcessId, that.bpmnProcessId) &&
        Objects.equals(treePath, that.treePath);
  }

  @Override
  public int hashCode() {
    return Objects
        .hash(super.hashCode(), errorType, errorMessage, errorMessageHash, state, flowNodeId,
            flowNodeInstanceKey, jobKey, processInstanceKey, creationTime, processDefinitionKey,
            bpmnProcessId, treePath, pending);
  }

  @Override
  public String toString() {
    return "IncidentEntity{" +
        "key=" + getKey() +
        ", errorType=" + errorType +
        ", errorMessage='" + errorMessage + '\'' +
        ", errorMessageHash=" + errorMessageHash +
        ", state=" + state +
        ", flowNodeId='" + flowNodeId + '\'' +
        ", flowNodeInstanceKey=" + flowNodeInstanceKey +
        ", jobKey=" + jobKey +
        ", processInstanceKey=" + processInstanceKey +
        ", creationTime=" + creationTime +
        ", processDefinitionKey=" + processDefinitionKey +
        ", bpmnProcessId=" + bpmnProcessId +
        ", treePath='" + treePath + '\'' +
        ", pending=" + pending +
        '}';
  }
}
