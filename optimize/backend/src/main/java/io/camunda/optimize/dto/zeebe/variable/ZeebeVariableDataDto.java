/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.variable;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class ZeebeVariableDataDto implements VariableRecordValue {

  private String name;
  private String value;
  private long scopeKey;
  private long processInstanceKey;
  private long processDefinitionKey;
  private String bpmnProcessId;
  private String tenantId;

  public ZeebeVariableDataDto() {}

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
  public String getName() {
    return name;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public long getScopeKey() {
    return scopeKey;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public long getElementInstanceKey() {
    return -1L; // not used in Optimize
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return -1L; // not used in Optimize
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public void setScopeKey(final long scopeKey) {
    this.scopeKey = scopeKey;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  public void setName(final String name) {
    this.name = name;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeVariableDataDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name, value, scopeKey, processInstanceKey, processDefinitionKey, bpmnProcessId, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeVariableDataDto that = (ZeebeVariableDataDto) o;
    return scopeKey == that.scopeKey
        && processInstanceKey == that.processInstanceKey
        && processDefinitionKey == that.processDefinitionKey
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "ZeebeVariableDataDto(name="
        + getName()
        + ", value="
        + getValue()
        + ", scopeKey="
        + getScopeKey()
        + ", processInstanceKey="
        + getProcessInstanceKey()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", bpmnProcessId="
        + getBpmnProcessId()
        + ", tenantId="
        + getTenantId()
        + ")";
  }
}
