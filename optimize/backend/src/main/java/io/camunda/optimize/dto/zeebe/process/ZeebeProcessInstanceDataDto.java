/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.process;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import org.apache.commons.lang3.StringUtils;

public class ZeebeProcessInstanceDataDto implements ProcessInstanceRecordValue {

  private int version;
  private String bpmnProcessId;
  private long processDefinitionKey;
  private long flowScopeKey;
  private BpmnElementType bpmnElementType;
  private long parentProcessInstanceKey;
  private long parentElementInstanceKey;
  private String elementId;
  private long processInstanceKey;
  private String tenantId;

  public ZeebeProcessInstanceDataDto() {}

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
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getVersion() {
    return version;
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
  public long getFlowScopeKey() {
    return flowScopeKey;
  }

  @Override
  public BpmnElementType getBpmnElementType() {
    return bpmnElementType;
  }

  @Override
  public long getParentProcessInstanceKey() {
    return parentProcessInstanceKey;
  }

  @Override
  public long getParentElementInstanceKey() {
    return parentElementInstanceKey;
  }

  @Override
  public BpmnEventType getBpmnEventType() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  public void setParentElementInstanceKey(final long parentElementInstanceKey) {
    this.parentElementInstanceKey = parentElementInstanceKey;
  }

  public void setParentProcessInstanceKey(final long parentProcessInstanceKey) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
  }

  public void setBpmnElementType(final BpmnElementType bpmnElementType) {
    this.bpmnElementType = bpmnElementType;
  }

  public void setFlowScopeKey(final long flowScopeKey) {
    this.flowScopeKey = flowScopeKey;
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

  public void setVersion(final int version) {
    this.version = version;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeProcessInstanceDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + getVersion();
    final Object $bpmnProcessId = getBpmnProcessId();
    result = result * PRIME + ($bpmnProcessId == null ? 43 : $bpmnProcessId.hashCode());
    final long $processDefinitionKey = getProcessDefinitionKey();
    result = result * PRIME + (int) ($processDefinitionKey >>> 32 ^ $processDefinitionKey);
    final long $flowScopeKey = getFlowScopeKey();
    result = result * PRIME + (int) ($flowScopeKey >>> 32 ^ $flowScopeKey);
    final Object $bpmnElementType = getBpmnElementType();
    result = result * PRIME + ($bpmnElementType == null ? 43 : $bpmnElementType.hashCode());
    final long $parentProcessInstanceKey = getParentProcessInstanceKey();
    result = result * PRIME + (int) ($parentProcessInstanceKey >>> 32 ^ $parentProcessInstanceKey);
    final long $parentElementInstanceKey = getParentElementInstanceKey();
    result = result * PRIME + (int) ($parentElementInstanceKey >>> 32 ^ $parentElementInstanceKey);
    final Object $elementId = getElementId();
    result = result * PRIME + ($elementId == null ? 43 : $elementId.hashCode());
    final long $processInstanceKey = getProcessInstanceKey();
    result = result * PRIME + (int) ($processInstanceKey >>> 32 ^ $processInstanceKey);
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ZeebeProcessInstanceDataDto)) {
      return false;
    }
    final ZeebeProcessInstanceDataDto other = (ZeebeProcessInstanceDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getVersion() != other.getVersion()) {
      return false;
    }
    final Object this$bpmnProcessId = getBpmnProcessId();
    final Object other$bpmnProcessId = other.getBpmnProcessId();
    if (this$bpmnProcessId == null
        ? other$bpmnProcessId != null
        : !this$bpmnProcessId.equals(other$bpmnProcessId)) {
      return false;
    }
    if (getProcessDefinitionKey() != other.getProcessDefinitionKey()) {
      return false;
    }
    if (getFlowScopeKey() != other.getFlowScopeKey()) {
      return false;
    }
    final Object this$bpmnElementType = getBpmnElementType();
    final Object other$bpmnElementType = other.getBpmnElementType();
    if (this$bpmnElementType == null
        ? other$bpmnElementType != null
        : !this$bpmnElementType.equals(other$bpmnElementType)) {
      return false;
    }
    if (getParentProcessInstanceKey() != other.getParentProcessInstanceKey()) {
      return false;
    }
    if (getParentElementInstanceKey() != other.getParentElementInstanceKey()) {
      return false;
    }
    final Object this$elementId = getElementId();
    final Object other$elementId = other.getElementId();
    if (this$elementId == null
        ? other$elementId != null
        : !this$elementId.equals(other$elementId)) {
      return false;
    }
    if (getProcessInstanceKey() != other.getProcessInstanceKey()) {
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
    return "ZeebeProcessInstanceDataDto(version="
        + getVersion()
        + ", bpmnProcessId="
        + getBpmnProcessId()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", flowScopeKey="
        + getFlowScopeKey()
        + ", bpmnElementType="
        + getBpmnElementType()
        + ", parentProcessInstanceKey="
        + getParentProcessInstanceKey()
        + ", parentElementInstanceKey="
        + getParentElementInstanceKey()
        + ", elementId="
        + getElementId()
        + ", processInstanceKey="
        + getProcessInstanceKey()
        + ", tenantId="
        + getTenantId()
        + ")";
  }

  public static final class Fields {

    public static final String version = "version";
    public static final String bpmnProcessId = "bpmnProcessId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String flowScopeKey = "flowScopeKey";
    public static final String bpmnElementType = "bpmnElementType";
    public static final String parentProcessInstanceKey = "parentProcessInstanceKey";
    public static final String parentElementInstanceKey = "parentElementInstanceKey";
    public static final String elementId = "elementId";
    public static final String processInstanceKey = "processInstanceKey";
    public static final String tenantId = "tenantId";
  }
}
