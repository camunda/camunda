/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.record.value;

import io.camunda.tasklist.zeebeimport.v870.record.RecordValueWithPayloadImpl;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class JobRecordValueImpl extends RecordValueWithPayloadImpl implements JobRecordValue {

  private String bpmnProcessId;
  private String elementId;
  private long elementInstanceKey;
  private long processInstanceKey;
  private long processDefinitionKey;
  private int processDefinitionVersion;
  private String type;
  private String worker;
  private long deadline;
  private Map<String, String> customHeaders;
  private int retries;
  private String errorMessage;
  private String errorCode;
  private String tenantId;
  private JobKind jobKind;
  private JobListenerEventType jobListenerEventType;
  private Set<String> changedAttributes;

  public JobRecordValueImpl() {}

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  public void setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  public void setWorker(final String worker) {
    this.worker = worker;
  }

  @Override
  public int getRetries() {
    return retries;
  }

  public void setRetries(final int retries) {
    this.retries = retries;
  }

  @Override
  public long getRetryBackoff() {
    return 0;
  }

  @Override
  public long getRecurringTime() {
    return 0;
  }

  @Override
  public long getDeadline() {
    return deadline;
  }

  public void setDeadline(final long deadline) {
    this.deadline = deadline;
  }

  @Override
  public long getTimeout() {
    return 0;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @Override
  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(final String errorCode) {
    this.errorCode = errorCode;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public JobKind getJobKind() {
    return jobKind;
  }

  public JobRecordValueImpl setJobKind(final JobKind jobKind) {
    this.jobKind = jobKind;
    return this;
  }

  @Override
  public JobListenerEventType getJobListenerEventType() {
    return jobListenerEventType;
  }

  public JobRecordValueImpl setJobListenerEventType(
      final JobListenerEventType jobListenerEventType) {
    this.jobListenerEventType = jobListenerEventType;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public Set<String> getChangedAttributes() {
    return changedAttributes;
  }

  public void setChangedAttributes(final Set<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        bpmnProcessId,
        elementId,
        elementInstanceKey,
        processInstanceKey,
        processDefinitionKey,
        processDefinitionVersion,
        type,
        worker,
        deadline,
        customHeaders,
        retries,
        errorMessage,
        errorCode,
        tenantId,
        jobKind,
        jobListenerEventType,
        changedAttributes);
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
        && processInstanceKey == that.processInstanceKey
        && processDefinitionKey == that.processDefinitionKey
        && processDefinitionVersion == that.processDefinitionVersion
        && deadline == that.deadline
        && retries == that.retries
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(type, that.type)
        && Objects.equals(worker, that.worker)
        && Objects.equals(customHeaders, that.customHeaders)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(errorCode, that.errorCode)
        && Objects.equals(tenantId, that.tenantId)
        && jobKind == that.jobKind
        && jobListenerEventType == that.jobListenerEventType
        && Objects.equals(changedAttributes, that.changedAttributes);
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
        + ", processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processDefinitionVersion="
        + processDefinitionVersion
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
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", errorCode='"
        + errorCode
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", jobKind="
        + jobKind
        + '\''
        + ", jobListenerEventType="
        + jobListenerEventType
        + ", changedAttributes="
        + changedAttributes
        + '}';
  }
}
