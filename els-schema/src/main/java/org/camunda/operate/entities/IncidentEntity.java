/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import java.time.OffsetDateTime;

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

  public IncidentEntity setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    IncidentEntity that = (IncidentEntity) o;

    if (errorType != that.errorType)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    if (errorMessageHash != null ? !errorMessageHash.equals(that.errorMessageHash) : that.errorMessageHash != null)
      return false;
    if (state != that.state)
      return false;
    if (flowNodeId != null ? !flowNodeId.equals(that.flowNodeId) : that.flowNodeId != null)
      return false;
    if (flowNodeInstanceKey != null ? !flowNodeInstanceKey.equals(that.flowNodeInstanceKey) : that.flowNodeInstanceKey != null)
      return false;
    if (jobKey != null ? !jobKey.equals(that.jobKey) : that.jobKey != null)
      return false;
    if (processInstanceKey != null ? !processInstanceKey.equals(that.processInstanceKey) : that.processInstanceKey != null)
      return false;
    if (processDefinitionKey != null ? !processDefinitionKey.equals(that.processDefinitionKey) : that.processDefinitionKey != null)
      return false;
    return creationTime != null ? creationTime.equals(that.creationTime) : that.creationTime == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (errorType != null ? errorType.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (errorMessageHash != null ? getErrorMessageHash().hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (flowNodeId != null ? flowNodeId.hashCode() : 0);
    result = 31 * result + (flowNodeInstanceKey != null ? flowNodeInstanceKey.hashCode() : 0);
    result = 31 * result + (jobKey != null ? jobKey.hashCode() : 0);
    result = 31 * result + (processInstanceKey != null ? processInstanceKey.hashCode() : 0);
    result = 31 * result + (creationTime != null ? creationTime.hashCode() : 0);
    result = 31 * result + (processDefinitionKey != null ? processDefinitionKey.hashCode() : 0);
    return result;
  }

}
