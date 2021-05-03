/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

public final class BpmnElementContextImpl implements BpmnElementContext {

  private long elementInstanceKey;
  private ProcessInstanceRecord recordValue;
  private ProcessInstanceIntent intent;

  private boolean reprocessingMode = false;

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getFlowScopeKey() {
    return recordValue.getFlowScopeKey();
  }

  @Override
  public long getProcessInstanceKey() {
    return recordValue.getProcessInstanceKey();
  }

  @Override
  public long getParentProcessInstanceKey() {
    return recordValue.getParentProcessInstanceKey();
  }

  @Override
  public long getParentElementInstanceKey() {
    return recordValue.getParentElementInstanceKey();
  }

  @Override
  public long getProcessDefinitionKey() {
    return recordValue.getProcessDefinitionKey();
  }

  @Override
  public int getProcessVersion() {
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
  public ProcessInstanceRecord getRecordValue() {
    return recordValue;
  }

  @Override
  public ProcessInstanceIntent getIntent() {
    return intent;
  }

  @Override
  public BpmnElementContext copy(
      final long elementInstanceKey,
      final ProcessInstanceRecord recordValue,
      final ProcessInstanceIntent intent) {

    final var copy = new BpmnElementContextImpl();
    copy.init(elementInstanceKey, recordValue, intent);
    copy.reprocessingMode = reprocessingMode;
    return copy;
  }

  public void init(
      final long elementInstanceKey,
      final ProcessInstanceRecord recordValue,
      final ProcessInstanceIntent intent) {
    this.elementInstanceKey = elementInstanceKey;
    this.recordValue = recordValue;
    this.intent = intent;
  }

  public void setReprocessingMode(final boolean reprocessingMode) {
    this.reprocessingMode = reprocessingMode;
  }

  @Override
  public boolean isInReprocessingMode() {
    return reprocessingMode;
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
        + ", processInstanceKey="
        + getProcessInstanceKey()
        + ", parentProcessInstanceKey="
        + getParentProcessInstanceKey()
        + ", parentElementInstanceKey="
        + getParentElementInstanceKey()
        + ", bpmnProcessId="
        + bufferAsString(getBpmnProcessId())
        + ", processVersion="
        + getProcessVersion()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + '}';
  }
}
