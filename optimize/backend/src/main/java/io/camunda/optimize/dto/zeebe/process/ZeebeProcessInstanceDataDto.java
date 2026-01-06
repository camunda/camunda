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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

  private final BpmnEventType bpmnEventType;
  private final List<List<Long>> elementInstancePath;
  private final List<Long> processDefinitionPath;
  private final List<Integer> callingElementPath;

  public ZeebeProcessInstanceDataDto() {
    bpmnEventType = BpmnEventType.UNSPECIFIED;
    elementInstancePath = new ArrayList<>();
    processDefinitionPath = new ArrayList<>();
    callingElementPath = new ArrayList<>();
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
    return bpmnEventType;
  }

  @Override
  public List<List<Long>> getElementInstancePath() {
    return elementInstancePath;
  }

  @Override
  public List<Long> getProcessDefinitionPath() {
    return processDefinitionPath;
  }

  @Override
  public List<Integer> getCallingElementPath() {
    return callingElementPath;
  }

  @Override
  public Set<String> getTags() {
    return Set.of();
  }

  @Override
  public long getRootProcessInstanceKey() {
    return -1L; // not used in Optimize
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
    return Objects.hash(
        version,
        bpmnProcessId,
        processDefinitionKey,
        flowScopeKey,
        bpmnElementType,
        parentProcessInstanceKey,
        parentElementInstanceKey,
        elementId,
        processInstanceKey,
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
    final ZeebeProcessInstanceDataDto that = (ZeebeProcessInstanceDataDto) o;
    return version == that.version
        && processDefinitionKey == that.processDefinitionKey
        && flowScopeKey == that.flowScopeKey
        && parentProcessInstanceKey == that.parentProcessInstanceKey
        && parentElementInstanceKey == that.parentElementInstanceKey
        && processInstanceKey == that.processInstanceKey
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(bpmnElementType, that.bpmnElementType)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(tenantId, that.tenantId);
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

  @SuppressWarnings("checkstyle:ConstantName")
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
