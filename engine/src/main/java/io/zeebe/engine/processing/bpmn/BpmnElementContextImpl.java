/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

public final class BpmnElementContextImpl implements BpmnElementContext {

  private long elementInstanceKey;
  private WorkflowInstanceRecord recordValue;
  private WorkflowInstanceIntent intent;

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getFlowScopeKey() {
    return recordValue.getFlowScopeKey();
  }

  @Override
  public long getWorkflowInstanceKey() {
    return recordValue.getWorkflowInstanceKey();
  }

  @Override
  public long getParentWorkflowInstanceKey() {
    return recordValue.getParentWorkflowInstanceKey();
  }

  @Override
  public long getParentElementInstanceKey() {
    return recordValue.getParentElementInstanceKey();
  }

  @Override
  public long getWorkflowKey() {
    return recordValue.getWorkflowKey();
  }

  @Override
  public int getWorkflowVersion() {
    return recordValue.getVersion();
  }

  @Override
  public DirectBuffer getBpmnProcessId() {
    return recordValue.getBpmnProcessIdBuffer();
  }

  @Override
  public DirectBuffer getElementId() {
    return recordValue.getElementIdBuffer();
  }

  @Override
  public BpmnElementType getBpmnElementType() {
    return recordValue.getBpmnElementType();
  }

  @Override
  public WorkflowInstanceRecord getRecordValue() {
    return recordValue;
  }

  @Override
  public WorkflowInstanceIntent getIntent() {
    return intent;
  }

  @Override
  public BpmnElementContext copy(
      final long elementInstanceKey,
      final WorkflowInstanceRecord recordValue,
      final WorkflowInstanceIntent intent) {

    final var copy = new BpmnElementContextImpl();
    copy.init(elementInstanceKey, recordValue, intent);
    return copy;
  }

  public void init(
      final long elementInstanceKey,
      final WorkflowInstanceRecord recordValue,
      final WorkflowInstanceIntent intent) {
    this.elementInstanceKey = elementInstanceKey;
    this.recordValue = recordValue;
    this.intent = intent;
  }

  @Override
  public String toString() {
    return "{"
        + "intent="
        + intent
        + ", elementId="
        + bufferAsString(getElementId())
        + ", bpmnElementType="
        + getBpmnElementType()
        + ", elementInstanceKey="
        + getElementInstanceKey()
        + ", flowScopeKey="
        + getFlowScopeKey()
        + ", workflowInstanceKey="
        + getWorkflowInstanceKey()
        + ", parentWorkflowInstanceKey="
        + getParentWorkflowInstanceKey()
        + ", parentElementInstanceKey="
        + getParentElementInstanceKey()
        + ", bpmnProcessId="
        + bufferAsString(getBpmnProcessId())
        + ", workflowVersion="
        + getWorkflowVersion()
        + ", workflowKey="
        + getWorkflowKey()
        + '}';
  }
}
