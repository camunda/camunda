/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v27.record.value;

import java.util.Map;
import java.util.Objects;
import org.camunda.operate.zeebeimport.v27.record.RecordValueWithPayloadImpl;
import io.zeebe.protocol.record.value.JobRecordValue;

public class JobRecordValueImpl extends RecordValueWithPayloadImpl implements JobRecordValue {

  private String bpmnProcessId;
  private String elementId;
  private long elementInstanceKey;
  private long workflowInstanceKey;
  private long workflowKey;
  private int workflowDefinitionVersion;

  private String type;
  private String worker;
  private long deadline;
  private Map<String, String> customHeaders;
  private int retries;
  private String errorMessage;
  private String errorCode;

  public JobRecordValueImpl() {
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public int getWorkflowDefinitionVersion() {
    return workflowDefinitionVersion;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setElementId(String elementId) {
    this.elementId = elementId;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public void setWorkflowKey(long workflowKey) {
    this.workflowKey = workflowKey;
  }

  public void setWorkflowDefinitionVersion(int workflowDefinitionVersion) {
    this.workflowDefinitionVersion = workflowDefinitionVersion;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  @Override
  public int getRetries() {
    return retries;
  }

  @Override
  public long getDeadline() {
    return deadline;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String getErrorCode() {
    return errorCode;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setWorker(String worker) {
    this.worker = worker;
  }

  public void setDeadline(long deadline) {
    this.deadline = deadline;
  }

  public void setCustomHeaders(Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  public void setRetries(int retries) {
    this.retries = retries;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
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
    final JobRecordValueImpl that = (JobRecordValueImpl) o;
    return elementInstanceKey == that.elementInstanceKey
        && workflowInstanceKey == that.workflowInstanceKey
        && workflowKey == that.workflowKey
        && workflowDefinitionVersion == that.workflowDefinitionVersion
        && retries == that.retries
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(type, that.type)
        && Objects.equals(worker, that.worker)
        && Objects.equals(deadline, that.deadline)
        && Objects.equals(customHeaders, that.customHeaders);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), bpmnProcessId, elementId,
        elementInstanceKey, workflowInstanceKey, workflowKey,
        workflowDefinitionVersion, type, worker, deadline,
        customHeaders, retries);
  }

  @Override
  public String toString() {
    return "JobRecordValueImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", elementId='"
        + elementId
        + '\''
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", workflowKey="
        + workflowKey
        + ", workflowDefinitionVersion="
        + workflowDefinitionVersion
        + ", type='"
        + type
        + '\''
        + ", worker='"
        + worker
        + '\''
        + ", deadline="
        + deadline
        + ", customHeaders="
        + customHeaders
        + ", retries="
        + retries
        + ", variables='"
        + getVariables()
        + '\''
        + '}';
  }
}
