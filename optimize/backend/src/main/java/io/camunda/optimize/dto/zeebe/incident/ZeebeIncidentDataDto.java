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

  public String getErrorMessage() {
    return this.errorMessage;
  }

  public String getBpmnProcessId() {
    return this.bpmnProcessId;
  }

  public String getElementId() {
    return this.elementId;
  }

  public long getElementInstanceKey() {
    return this.elementInstanceKey;
  }

  public long getProcessInstanceKey() {
    return this.processInstanceKey;
  }

  public long getProcessDefinitionKey() {
    return this.processDefinitionKey;
  }

  public long getJobKey() {
    return this.jobKey;
  }

  public ErrorType getErrorType() {
    return this.errorType;
  }

  public long getVariableScopeKey() {
    return this.variableScopeKey;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
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

  public void setProcessInstanceKey(long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public void setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setJobKey(long jobKey) {
    this.jobKey = jobKey;
  }

  public void setErrorType(ErrorType errorType) {
    this.errorType = errorType;
  }

  public void setVariableScopeKey(long variableScopeKey) {
    this.variableScopeKey = variableScopeKey;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String toString() {
    return "ZeebeIncidentDataDto(errorMessage="
        + this.getErrorMessage()
        + ", bpmnProcessId="
        + this.getBpmnProcessId()
        + ", elementId="
        + this.getElementId()
        + ", elementInstanceKey="
        + this.getElementInstanceKey()
        + ", processInstanceKey="
        + this.getProcessInstanceKey()
        + ", processDefinitionKey="
        + this.getProcessDefinitionKey()
        + ", jobKey="
        + this.getJobKey()
        + ", errorType="
        + this.getErrorType()
        + ", variableScopeKey="
        + this.getVariableScopeKey()
        + ", tenantId="
        + this.getTenantId()
        + ")";
  }

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
    final Object this$errorMessage = this.getErrorMessage();
    final Object other$errorMessage = other.getErrorMessage();
    if (this$errorMessage == null
        ? other$errorMessage != null
        : !this$errorMessage.equals(other$errorMessage)) {
      return false;
    }
    final Object this$bpmnProcessId = this.getBpmnProcessId();
    final Object other$bpmnProcessId = other.getBpmnProcessId();
    if (this$bpmnProcessId == null
        ? other$bpmnProcessId != null
        : !this$bpmnProcessId.equals(other$bpmnProcessId)) {
      return false;
    }
    final Object this$elementId = this.getElementId();
    final Object other$elementId = other.getElementId();
    if (this$elementId == null
        ? other$elementId != null
        : !this$elementId.equals(other$elementId)) {
      return false;
    }
    if (this.getElementInstanceKey() != other.getElementInstanceKey()) {
      return false;
    }
    if (this.getProcessInstanceKey() != other.getProcessInstanceKey()) {
      return false;
    }
    if (this.getProcessDefinitionKey() != other.getProcessDefinitionKey()) {
      return false;
    }
    if (this.getJobKey() != other.getJobKey()) {
      return false;
    }
    final Object this$errorType = this.getErrorType();
    final Object other$errorType = other.getErrorType();
    if (this$errorType == null
        ? other$errorType != null
        : !this$errorType.equals(other$errorType)) {
      return false;
    }
    if (this.getVariableScopeKey() != other.getVariableScopeKey()) {
      return false;
    }
    final Object this$tenantId = this.getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeIncidentDataDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $errorMessage = this.getErrorMessage();
    result = result * PRIME + ($errorMessage == null ? 43 : $errorMessage.hashCode());
    final Object $bpmnProcessId = this.getBpmnProcessId();
    result = result * PRIME + ($bpmnProcessId == null ? 43 : $bpmnProcessId.hashCode());
    final Object $elementId = this.getElementId();
    result = result * PRIME + ($elementId == null ? 43 : $elementId.hashCode());
    final long $elementInstanceKey = this.getElementInstanceKey();
    result = result * PRIME + (int) ($elementInstanceKey >>> 32 ^ $elementInstanceKey);
    final long $processInstanceKey = this.getProcessInstanceKey();
    result = result * PRIME + (int) ($processInstanceKey >>> 32 ^ $processInstanceKey);
    final long $processDefinitionKey = this.getProcessDefinitionKey();
    result = result * PRIME + (int) ($processDefinitionKey >>> 32 ^ $processDefinitionKey);
    final long $jobKey = this.getJobKey();
    result = result * PRIME + (int) ($jobKey >>> 32 ^ $jobKey);
    final Object $errorType = this.getErrorType();
    result = result * PRIME + ($errorType == null ? 43 : $errorType.hashCode());
    final long $variableScopeKey = this.getVariableScopeKey();
    result = result * PRIME + (int) ($variableScopeKey >>> 32 ^ $variableScopeKey);
    final Object $tenantId = this.getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    return result;
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
