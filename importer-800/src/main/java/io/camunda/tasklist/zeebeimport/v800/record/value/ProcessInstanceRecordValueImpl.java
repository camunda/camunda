/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.zeebeimport.v800.record.value;

import io.camunda.tasklist.zeebeimport.v800.record.RecordValueWithPayloadImpl;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

public class ProcessInstanceRecordValueImpl extends RecordValueWithPayloadImpl
    implements ProcessInstanceRecordValue {

  private String bpmnProcessId;
  private String elementId;
  private int version;
  private long processDefinitionKey;
  private long processInstanceKey;
  private long flowScopeKey;
  private BpmnElementType bpmnElementType;
  private long parentProcessInstanceKey;
  private long parentElementInstanceKey;

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
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (elementId != null ? elementId.hashCode() : 0);
    result = 31 * result + version;
    result = 31 * result + (int) (processDefinitionKey ^ (processDefinitionKey >>> 32));
    result = 31 * result + (int) (processInstanceKey ^ (processInstanceKey >>> 32));
    result = 31 * result + (int) (flowScopeKey ^ (flowScopeKey >>> 32));
    result = 31 * result + (bpmnElementType != null ? bpmnElementType.hashCode() : 0);
    result = 31 * result + (int) (parentProcessInstanceKey ^ (parentProcessInstanceKey >>> 32));
    result = 31 * result + (int) (parentElementInstanceKey ^ (parentElementInstanceKey >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object o) {
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

    if (version != that.version) {
      return false;
    }
    if (processDefinitionKey != that.processDefinitionKey) {
      return false;
    }
    if (processInstanceKey != that.processInstanceKey) {
      return false;
    }
    if (flowScopeKey != that.flowScopeKey) {
      return false;
    }
    if (bpmnProcessId != null
        ? !bpmnProcessId.equals(that.bpmnProcessId)
        : that.bpmnProcessId != null) {
      return false;
    }
    if (elementId != null ? !elementId.equals(that.elementId) : that.elementId != null) {
      return false;
    }
    if (bpmnElementType != that.bpmnElementType) {
      return false;
    }
    if (parentProcessInstanceKey != that.parentProcessInstanceKey) {
      return false;
    }
    return parentElementInstanceKey != that.parentElementInstanceKey;
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
        + ", parentProcessInstanceKey="
        + parentProcessInstanceKey
        + ", parentElementInstanceKey="
        + parentElementInstanceKey
        + "} "
        + super.toString();
  }
}
