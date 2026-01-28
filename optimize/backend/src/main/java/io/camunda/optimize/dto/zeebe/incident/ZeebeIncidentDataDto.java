/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.incident;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class ZeebeIncidentDataDto implements IncidentRecordValue {

  private String errorMessage;
  private String bpmnProcessId;
  private String elementId;
  private long elementInstanceKey;
  private long processInstanceKey;
  private long processDefinitionKey;
  private long jobKey;
  private ErrorType errorType;
  private long variableScopeKey;
  private String tenantId;

  public ZeebeIncidentDataDto() {}

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public String getTenantId() {
    return StringUtils.isEmpty(tenantId) ? ZEEBE_DEFAULT_TENANT_ID : tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public ErrorType getErrorType() {
    return errorType;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
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
  public long getJobKey() {
    return jobKey;
  }

  @Override
  public long getVariableScopeKey() {
    return variableScopeKey;
  }

  @JsonIgnore
  @Override
  public List<List<Long>> getElementInstancePath() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @JsonIgnore
  @Override
  public List<Long> getProcessDefinitionPath() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @JsonIgnore
  @Override
  public List<Integer> getCallingElementPath() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public long getRootProcessInstanceKey() {
    return -1L; // Not used in Optimize
  }

  public void setVariableScopeKey(final long variableScopeKey) {
    this.variableScopeKey = variableScopeKey;
  }

  public void setJobKey(final long jobKey) {
    this.jobKey = jobKey;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setErrorType(final ErrorType errorType) {
    this.errorType = errorType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        errorMessage,
        bpmnProcessId,
        elementId,
        elementInstanceKey,
        processInstanceKey,
        processDefinitionKey,
        jobKey,
        errorType,
        variableScopeKey,
        tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeIncidentDataDto that = (ZeebeIncidentDataDto) o;
    return elementInstanceKey == that.elementInstanceKey
        && processInstanceKey == that.processInstanceKey
        && processDefinitionKey == that.processDefinitionKey
        && jobKey == that.jobKey
        && variableScopeKey == that.variableScopeKey
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(errorType, that.errorType)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "ZeebeIncidentDataDto(errorMessage="
        + getErrorMessage()
        + ", bpmnProcessId="
        + getBpmnProcessId()
        + ", elementId="
        + getElementId()
        + ", elementInstanceKey="
        + getElementInstanceKey()
        + ", processInstanceKey="
        + getProcessInstanceKey()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", jobKey="
        + getJobKey()
        + ", errorType="
        + getErrorType()
        + ", variableScopeKey="
        + getVariableScopeKey()
        + ", tenantId="
        + getTenantId()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String errorMessage = "errorMessage";
    public static final String bpmnProcessId = "bpmnProcessId";
    public static final String elementId = "elementId";
    public static final String elementInstanceKey = "elementInstanceKey";
    public static final String processInstanceKey = "processInstanceKey";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String jobKey = "jobKey";
    public static final String errorType = "errorType";
    public static final String variableScopeKey = "variableScopeKey";
    public static final String tenantId = "tenantId";
  }
}
