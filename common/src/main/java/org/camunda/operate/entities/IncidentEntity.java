/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import io.zeebe.protocol.record.value.ErrorType;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class IncidentEntity extends OperateZeebeEntity<IncidentEntity> {

  private static final Map<ErrorType, String> ErrorType2Title;
  static {
    ErrorType2Title = new HashMap<>();
    ErrorType2Title.put(ErrorType.UNKNOWN, "Unknown");
    ErrorType2Title.put(ErrorType.IO_MAPPING_ERROR, "I/O mapping error");
    ErrorType2Title.put(ErrorType.JOB_NO_RETRIES, "No more retries left");
    ErrorType2Title.put(ErrorType.CONDITION_ERROR, "Condition error");
    ErrorType2Title.put(ErrorType.EXTRACT_VALUE_ERROR, "Extract value error");
    ErrorType2Title.put(ErrorType.CALLED_ELEMENT_ERROR, "Called element error");
    ErrorType2Title.put(ErrorType.UNHANDLED_ERROR_EVENT, "Unhandled error event");
  }

  private ErrorType errorType;

  private String errorMessage;
  
  // Is only used by binding to ES results
  private Integer errorMessageHash;

  private IncidentState state;

  private String flowNodeId;

  private Long flowNodeInstanceKey;

  private Long jobKey;

  private Long workflowInstanceKey;

  private OffsetDateTime creationTime;

  private Long workflowKey;

  public ErrorType getErrorType() {
    return errorType;
  }

  public IncidentEntity setErrorType(ErrorType errorType) {
    this.errorType = errorType;
    return this;
  }
  
  public static String getErrorTypeTitle(ErrorType errorType) {
    return ErrorType2Title.getOrDefault(errorType, "Unknown error");
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

  public Long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public IncidentEntity setWorkflowInstanceKey(Long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public IncidentEntity setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public IncidentEntity setWorkflowKey(Long workflowKey) {
    this.workflowKey = workflowKey;
    return this;
  }

  public Long getWorkflowKey() {
    return workflowKey;
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
    if (workflowInstanceKey != null ? !workflowInstanceKey.equals(that.workflowInstanceKey) : that.workflowInstanceKey != null)
      return false;
    if (workflowKey != null ? !workflowKey.equals(that.workflowKey) : that.workflowKey != null)
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
    result = 31 * result + (workflowInstanceKey != null ? workflowInstanceKey.hashCode() : 0);
    result = 31 * result + (creationTime != null ? creationTime.hashCode() : 0);
    result = 31 * result + (workflowKey != null ? workflowKey.hashCode() : 0);
    return result;
  }

}
