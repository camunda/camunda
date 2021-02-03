/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v1_0.record.value;

import io.zeebe.protocol.record.value.BpmnElementType;
import org.camunda.operate.zeebeimport.v1_0.record.RecordValueWithPayloadImpl;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;

public class WorkflowInstanceRecordValueImpl extends RecordValueWithPayloadImpl
    implements WorkflowInstanceRecordValue {
  private String bpmnProcessId;
  private String elementId;
  private int version;
  private long workflowKey;
  private long workflowInstanceKey;
  private long flowScopeKey;
  private BpmnElementType bpmnElementType;
  private long parentWorkflowInstanceKey;
  private long parentElementInstanceKey;

  public WorkflowInstanceRecordValueImpl() {
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
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
  public long getParentWorkflowInstanceKey() {
    return parentWorkflowInstanceKey;
  }

  @Override
  public long getParentElementInstanceKey() {
    return parentElementInstanceKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    WorkflowInstanceRecordValueImpl that = (WorkflowInstanceRecordValueImpl) o;

    if (version != that.version)
      return false;
    if (workflowKey != that.workflowKey)
      return false;
    if (workflowInstanceKey != that.workflowInstanceKey)
      return false;
    if (flowScopeKey != that.flowScopeKey)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (elementId != null ? !elementId.equals(that.elementId) : that.elementId != null)
      return false;
    if (bpmnElementType != that.bpmnElementType)
      return false;
    if (parentWorkflowInstanceKey != that.parentWorkflowInstanceKey)
      return false;
    return parentElementInstanceKey != that.parentElementInstanceKey;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (elementId != null ? elementId.hashCode() : 0);
    result = 31 * result + version;
    result = 31 * result + (int) (workflowKey ^ (workflowKey >>> 32));
    result = 31 * result + (int) (workflowInstanceKey ^ (workflowInstanceKey >>> 32));
    result = 31 * result + (int) (flowScopeKey ^ (flowScopeKey >>> 32));
    result = 31 * result + (bpmnElementType != null ? bpmnElementType.hashCode() : 0);
    result = 31 * result + (int) (parentWorkflowInstanceKey ^ (parentWorkflowInstanceKey >>> 32));
    result = 31 * result + (int) (parentElementInstanceKey ^ (parentElementInstanceKey >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "WorkflowInstanceRecordValueImpl{" + "bpmnProcessId='" + bpmnProcessId + '\'' + ", elementId='" + elementId + '\'' + ", version=" + version
      + ", workflowKey=" + workflowKey + ", workflowInstanceKey=" + workflowInstanceKey + ", flowScopeKey=" + flowScopeKey + ", bpmnElementType="
      + bpmnElementType + ", parentWorkflowInstanceKey=" + parentWorkflowInstanceKey + ", parentElementInstanceKey=" + parentElementInstanceKey + "} " + super.toString();
  }
}
