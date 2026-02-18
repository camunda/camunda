/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import java.util.Set;
import org.agrona.DirectBuffer;

public final class BpmnElementContextImpl implements BpmnElementContext {

  private long elementInstanceKey;
  private ProcessInstanceRecord recordValue;
  private ProcessInstanceIntent intent;

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
  public long getRootProcessInstanceKey() {
    return recordValue.getRootProcessInstanceKey();
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
  public String getTenantId() {
    return recordValue.getTenantId();
  }

  @Override
  public BpmnEventType getBpmnEventType() {
    return recordValue.getBpmnEventType();
  }

  @Override
  public Set<String> getTags() {
    return recordValue.getTags();
  }

  @Override
  public String getBusinessId() {
    return recordValue.getBusinessId();
  }

  @Override
  public BpmnElementContext copy(
      final long elementInstanceKey,
      final ProcessInstanceRecord recordValue,
      final ProcessInstanceIntent intent) {

    final var copy = new BpmnElementContextImpl();
    copy.init(elementInstanceKey, recordValue, intent);
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
        + ", tenantId="
        + getTenantId()
        + '}';
  }
}
