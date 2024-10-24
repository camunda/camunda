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

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setJobKey(final long jobKey) {
    this.jobKey = jobKey;
  }

  public void setErrorType(final ErrorType errorType) {
    this.errorType = errorType;
  }

  public void setVariableScopeKey(final long variableScopeKey) {
    this.variableScopeKey = variableScopeKey;
  }

  public void setTenantId(final String tenantId) {
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
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeIncidentDataDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
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
