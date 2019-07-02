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

public class IncidentEntity extends OperateZeebeEntity {

  private static final Map<ErrorType, String> ErrorType2Title;
  static {
    ErrorType2Title = new HashMap<>();
    ErrorType2Title.put(ErrorType.UNKNOWN, "Unknown");
    ErrorType2Title.put(ErrorType.IO_MAPPING_ERROR, "I/O mapping error");
    ErrorType2Title.put(ErrorType.JOB_NO_RETRIES, "No more retries left");
    ErrorType2Title.put(ErrorType.CONDITION_ERROR, "Condition error");
    ErrorType2Title.put(ErrorType.EXTRACT_VALUE_ERROR, "Extract value error");
  }

  private ErrorType errorType;

  private String errorMessage;

  private IncidentState state;

  private String flowNodeId;

  private Long flowNodeInstanceKey;

  private Long jobKey;

  private Long workflowInstanceId;

  private OffsetDateTime creationTime;

  public ErrorType getErrorType() {
    return errorType;
  }

  public void setErrorType(ErrorType errorType) {
    this.errorType = errorType;
  }

  public static String getErrorTypeTitle(ErrorType errorType) {
    return ErrorType2Title.getOrDefault(errorType, "Unknown error");
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public IncidentState getState() {
    return state;
  }

  public void setState(IncidentState state) {
    this.state = state;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public void setFlowNodeInstanceKey(Long flowNodeInstanceId) {
    this.flowNodeInstanceKey = flowNodeInstanceId;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public void setJobKey(Long jobKey) {
    this.jobKey = jobKey;
  }

  public Long getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(Long workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
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
    if (state != that.state)
      return false;
    if (flowNodeId != null ? !flowNodeId.equals(that.flowNodeId) : that.flowNodeId != null)
      return false;
    if (flowNodeInstanceKey != null ? !flowNodeInstanceKey.equals(that.flowNodeInstanceKey) : that.flowNodeInstanceKey != null)
      return false;
    if (jobKey != null ? !jobKey.equals(that.jobKey) : that.jobKey != null)
      return false;
    if (workflowInstanceId != null ? !workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId != null)
      return false;
    return creationTime != null ? creationTime.equals(that.creationTime) : that.creationTime == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (errorType != null ? errorType.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (flowNodeId != null ? flowNodeId.hashCode() : 0);
    result = 31 * result + (flowNodeInstanceKey != null ? flowNodeInstanceKey.hashCode() : 0);
    result = 31 * result + (jobKey != null ? jobKey.hashCode() : 0);
    result = 31 * result + (workflowInstanceId != null ? workflowInstanceId.hashCode() : 0);
    result = 31 * result + (creationTime != null ? creationTime.hashCode() : 0);
    return result;
  }
}
