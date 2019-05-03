/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import io.zeebe.protocol.ErrorType;

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

  private String flowNodeInstanceId;

  private String jobId;

  private String workflowInstanceId;

  private String workflowId;
  
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

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public void setFlowNodeInstanceId(String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
  }

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
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
    if (flowNodeInstanceId != null ? !flowNodeInstanceId.equals(that.flowNodeInstanceId) : that.flowNodeInstanceId != null)
      return false;
    if (jobId != null ? !jobId.equals(that.jobId) : that.jobId != null)
      return false;
    if (workflowInstanceId != null ? !workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId != null)
      return false;
    if (workflowId != null ? !workflowId.equals(that.workflowId) : that.workflowId != null)
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
    result = 31 * result + (flowNodeInstanceId != null ? flowNodeInstanceId.hashCode() : 0);
    result = 31 * result + (jobId != null ? jobId.hashCode() : 0);
    result = 31 * result + (workflowInstanceId != null ? workflowInstanceId.hashCode() : 0);
    result = 31 * result + (workflowId != null ? workflowId.hashCode() : 0);
    result = 31 * result + (creationTime != null ? creationTime.hashCode() : 0);
    return result;
  }
}
