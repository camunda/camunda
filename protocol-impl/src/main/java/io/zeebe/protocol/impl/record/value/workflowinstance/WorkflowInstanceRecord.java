/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.record.value.workflowinstance;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import org.agrona.DirectBuffer;

public final class WorkflowInstanceRecord extends UnifiedRecordValue
    implements WorkflowInstanceRecordValue {

  public static final String PROP_WORKFLOW_BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String PROP_WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";
  public static final String PROP_WORKFLOW_ELEMENT_ID = "elementId";
  public static final String PROP_WORKFLOW_VERSION = "version";
  public static final String PROP_WORKFLOW_KEY = "workflowKey";
  public static final String PROP_WORKFLOW_BPMN_TYPE = "bpmnElementType";
  public static final String PROP_WORKFLOW_SCOPE_KEY = "flowScopeKey";

  private final StringProperty bpmnProcessIdProp =
      new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID, "");
  private final IntegerProperty versionProp = new IntegerProperty(PROP_WORKFLOW_VERSION, -1);
  private final LongProperty workflowKeyProp = new LongProperty(PROP_WORKFLOW_KEY, -1L);

  private final LongProperty workflowInstanceKeyProp =
      new LongProperty(PROP_WORKFLOW_INSTANCE_KEY, -1L);
  private final StringProperty elementIdProp = new StringProperty(PROP_WORKFLOW_ELEMENT_ID, "");

  private final LongProperty flowScopeKeyProp = new LongProperty(PROP_WORKFLOW_SCOPE_KEY, -1L);

  private final EnumProperty<BpmnElementType> bpmnElementTypeProp =
      new EnumProperty<>(
          PROP_WORKFLOW_BPMN_TYPE, BpmnElementType.class, BpmnElementType.UNSPECIFIED);

  private final LongProperty parentWorkflowInstanceKeyProp =
      new LongProperty("parentWorkflowInstanceKey", -1L);
  private final LongProperty parentElementInstanceKeyProp =
      new LongProperty("parentElementInstanceKey", -1L);

  public WorkflowInstanceRecord() {
    declareProperty(bpmnProcessIdProp)
        .declareProperty(versionProp)
        .declareProperty(workflowKeyProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(flowScopeKeyProp)
        .declareProperty(bpmnElementTypeProp)
        .declareProperty(parentWorkflowInstanceKeyProp)
        .declareProperty(parentElementInstanceKeyProp);
  }

  public void wrap(final WorkflowInstanceRecord record) {
    elementIdProp.setValue(record.getElementIdBuffer());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    flowScopeKeyProp.setValue(record.getFlowScopeKey());
    versionProp.setValue(record.getVersion());
    workflowKeyProp.setValue(record.getWorkflowKey());
    workflowInstanceKeyProp.setValue(record.getWorkflowInstanceKey());
    bpmnElementTypeProp.setValue(record.getBpmnElementType());
    parentWorkflowInstanceKeyProp.setValue(record.getParentWorkflowInstanceKey());
    parentElementInstanceKeyProp.setValue(record.getParentElementInstanceKey());
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public WorkflowInstanceRecord setWorkflowInstanceKey(final long workflowInstanceKey) {
    workflowInstanceKeyProp.setValue(workflowInstanceKey);
    return this;
  }

  public WorkflowInstanceRecord setBpmnProcessId(
      final DirectBuffer directBuffer, final int offset, final int length) {
    bpmnProcessIdProp.setValue(directBuffer, offset, length);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  @Override
  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementIdProp.getValue());
  }

  @Override
  public long getFlowScopeKey() {
    return flowScopeKeyProp.getValue();
  }

  @Override
  public BpmnElementType getBpmnElementType() {
    return bpmnElementTypeProp.getValue();
  }

  @Override
  public long getParentWorkflowInstanceKey() {
    return parentWorkflowInstanceKeyProp.getValue();
  }

  @Override
  public long getParentElementInstanceKey() {
    return parentElementInstanceKeyProp.getValue();
  }

  public WorkflowInstanceRecord setParentElementInstanceKey(final long parentElementInstanceKey) {
    parentElementInstanceKeyProp.setValue(parentElementInstanceKey);
    return this;
  }

  public WorkflowInstanceRecord setParentWorkflowInstanceKey(final long parentWorkflowInstanceKey) {
    parentWorkflowInstanceKeyProp.setValue(parentWorkflowInstanceKey);
    return this;
  }

  public WorkflowInstanceRecord setBpmnElementType(final BpmnElementType bpmnType) {
    bpmnElementTypeProp.setValue(bpmnType);
    return this;
  }

  public WorkflowInstanceRecord setFlowScopeKey(final long flowScopeKey) {
    flowScopeKeyProp.setValue(flowScopeKey);
    return this;
  }

  public WorkflowInstanceRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public WorkflowInstanceRecord setElementId(final DirectBuffer elementId) {
    return setElementId(elementId, 0, elementId.capacity());
  }

  public WorkflowInstanceRecord setWorkflowKey(final long workflowKey) {
    workflowKeyProp.setValue(workflowKey);
    return this;
  }

  public WorkflowInstanceRecord setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  public WorkflowInstanceRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public WorkflowInstanceRecord setBpmnProcessId(final DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer);
    return this;
  }

  public WorkflowInstanceRecord setElementId(
      final DirectBuffer elementId, final int offset, final int length) {
    elementIdProp.setValue(elementId, offset, length);
    return this;
  }
}
