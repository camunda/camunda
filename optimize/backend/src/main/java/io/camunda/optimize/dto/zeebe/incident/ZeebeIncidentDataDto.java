/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.incident;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
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

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeIncidentDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $errorMessage = getErrorMessage();
    result = result * PRIME + ($errorMessage == null ? 43 : $errorMessage.hashCode());
    final Object $bpmnProcessId = getBpmnProcessId();
    result = result * PRIME + ($bpmnProcessId == null ? 43 : $bpmnProcessId.hashCode());
    final Object $elementId = getElementId();
    result = result * PRIME + ($elementId == null ? 43 : $elementId.hashCode());
    final long $elementInstanceKey = getElementInstanceKey();
    result = result * PRIME + (int) ($elementInstanceKey >>> 32 ^ $elementInstanceKey);
    final long $processInstanceKey = getProcessInstanceKey();
    result = result * PRIME + (int) ($processInstanceKey >>> 32 ^ $processInstanceKey);
    final long $processDefinitionKey = getProcessDefinitionKey();
    result = result * PRIME + (int) ($processDefinitionKey >>> 32 ^ $processDefinitionKey);
    final long $jobKey = getJobKey();
    result = result * PRIME + (int) ($jobKey >>> 32 ^ $jobKey);
    final Object $errorType = getErrorType();
    result = result * PRIME + ($errorType == null ? 43 : $errorType.hashCode());
    final long $variableScopeKey = getVariableScopeKey();
    result = result * PRIME + (int) ($variableScopeKey >>> 32 ^ $variableScopeKey);
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ZeebeIncidentDataDto)) {
      return false;
    }
    final ZeebeIncidentDataDto other = (ZeebeIncidentDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$errorMessage = getErrorMessage();
    final Object other$errorMessage = other.getErrorMessage();
    if (this$errorMessage == null
        ? other$errorMessage != null
        : !this$errorMessage.equals(other$errorMessage)) {
      return false;
    }
    final Object this$bpmnProcessId = getBpmnProcessId();
    final Object other$bpmnProcessId = other.getBpmnProcessId();
    if (this$bpmnProcessId == null
        ? other$bpmnProcessId != null
        : !this$bpmnProcessId.equals(other$bpmnProcessId)) {
      return false;
    }
    final Object this$elementId = getElementId();
    final Object other$elementId = other.getElementId();
    if (this$elementId == null
        ? other$elementId != null
        : !this$elementId.equals(other$elementId)) {
      return false;
    }
    if (getElementInstanceKey() != other.getElementInstanceKey()) {
      return false;
    }
    if (getProcessInstanceKey() != other.getProcessInstanceKey()) {
      return false;
    }
    if (getProcessDefinitionKey() != other.getProcessDefinitionKey()) {
      return false;
    }
    if (getJobKey() != other.getJobKey()) {
      return false;
    }
    final Object this$errorType = getErrorType();
    final Object other$errorType = other.getErrorType();
    if (this$errorType == null
        ? other$errorType != null
        : !this$errorType.equals(other$errorType)) {
      return false;
    }
    if (getVariableScopeKey() != other.getVariableScopeKey()) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    return true;
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
