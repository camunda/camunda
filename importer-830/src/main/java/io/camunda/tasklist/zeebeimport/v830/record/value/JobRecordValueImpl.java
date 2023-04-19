/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v830.record.value;

import io.camunda.tasklist.zeebeimport.v830.record.RecordValueWithPayloadImpl;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.Map;
import java.util.Objects;

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

  public JobRecordValueImpl() {}

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
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

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String getErrorCode() {
    return errorCode;
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
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessDefinitionVersion(int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setElementId(String elementId) {
    this.elementId = elementId;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setDeadline(long deadline) {
    this.deadline = deadline;
  }

  public void setRetries(int retries) {
    this.retries = retries;
  }

  public void setWorker(String worker) {
    this.worker = worker;
  }

  public void setCustomHeaders(Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  public void setType(String type) {
    this.type = type;
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
        retries);
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
        && retries == that.retries
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(type, that.type)
        && Objects.equals(worker, that.worker)
        && Objects.equals(deadline, that.deadline)
        && Objects.equals(customHeaders, that.customHeaders);
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
        + ", variables='"
        + getVariables()
        + '\''
        + '}';
  }
}
