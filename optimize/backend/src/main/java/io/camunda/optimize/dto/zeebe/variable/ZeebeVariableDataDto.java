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
  public String getBpmnProcessId() {
    return bpmnProcessId;
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
    final int PRIME = 59;
    int result = 1;
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    final long $scopeKey = getScopeKey();
    result = result * PRIME + (int) ($scopeKey >>> 32 ^ $scopeKey);
    final long $processInstanceKey = getProcessInstanceKey();
    result = result * PRIME + (int) ($processInstanceKey >>> 32 ^ $processInstanceKey);
    final long $processDefinitionKey = getProcessDefinitionKey();
    result = result * PRIME + (int) ($processDefinitionKey >>> 32 ^ $processDefinitionKey);
    final Object $bpmnProcessId = getBpmnProcessId();
    result = result * PRIME + ($bpmnProcessId == null ? 43 : $bpmnProcessId.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ZeebeVariableDataDto)) {
      return false;
    }
    final ZeebeVariableDataDto other = (ZeebeVariableDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    if (getScopeKey() != other.getScopeKey()) {
      return false;
    }
    if (getProcessInstanceKey() != other.getProcessInstanceKey()) {
      return false;
    }
    if (getProcessDefinitionKey() != other.getProcessDefinitionKey()) {
      return false;
    }
    final Object this$bpmnProcessId = getBpmnProcessId();
    final Object other$bpmnProcessId = other.getBpmnProcessId();
    if (this$bpmnProcessId == null
        ? other$bpmnProcessId != null
        : !this$bpmnProcessId.equals(other$bpmnProcessId)) {
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
