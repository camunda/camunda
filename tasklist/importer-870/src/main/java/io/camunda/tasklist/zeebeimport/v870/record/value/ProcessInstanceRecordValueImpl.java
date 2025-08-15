/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.record.value;

import io.camunda.tasklist.zeebeimport.v870.record.RecordValueWithPayloadImpl;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ProcessInstanceRecordValueImpl extends RecordValueWithPayloadImpl
    implements ProcessInstanceRecordValue {

  private String bpmnProcessId;
  private String elementId;
  private int version;
  private long processDefinitionKey;
  private long processInstanceKey;
  private long flowScopeKey;
  private BpmnElementType bpmnElementType;
  private BpmnEventType bpmnEventType;
  private long parentProcessInstanceKey;
  private long parentElementInstanceKey;
  private String tenantId;

  private List<List<Long>> elementInstancePath;
  private List<Long> processDefinitionPath;
  private List<Integer> callingElementPath;
  private Set<String> tags;

  public ProcessInstanceRecordValueImpl() {}

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
    return tags;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        bpmnProcessId,
        elementId,
        version,
        processDefinitionKey,
        processInstanceKey,
        flowScopeKey,
        bpmnElementType,
        bpmnEventType,
        parentProcessInstanceKey,
        parentElementInstanceKey,
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
    if (!super.equals(o)) {
      return false;
    }
    final ProcessInstanceRecordValueImpl that = (ProcessInstanceRecordValueImpl) o;
    return version == that.version
        && processDefinitionKey == that.processDefinitionKey
        && processInstanceKey == that.processInstanceKey
        && flowScopeKey == that.flowScopeKey
        && parentProcessInstanceKey == that.parentProcessInstanceKey
        && parentElementInstanceKey == that.parentElementInstanceKey
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(tenantId, that.tenantId)
        && bpmnElementType == that.bpmnElementType
        && bpmnEventType == that.bpmnEventType;
  }

  @Override
  public String toString() {
    return "ProcessInstanceRecordValueImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", elementId='"
        + elementId
        + '\''
        + ", version="
        + version
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", flowScopeKey="
        + flowScopeKey
        + ", bpmnElementType="
        + bpmnElementType
        + ", bpmnEventType="
        + bpmnEventType
        + ", parentProcessInstanceKey="
        + parentProcessInstanceKey
        + ", parentElementInstanceKey="
        + parentElementInstanceKey
        + ", tenantId="
        + tenantId
        + '}';
  }
}
