/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.record.value.workflowinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRelated;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class WorkflowInstanceRecord extends UnifiedRecordValue
    implements WorkflowInstanceRelated, WorkflowInstanceRecordValue {

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

  public WorkflowInstanceRecord() {
    this.declareProperty(bpmnProcessIdProp)
        .declareProperty(versionProp)
        .declareProperty(workflowKeyProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(flowScopeKeyProp)
        .declareProperty(bpmnElementTypeProp);
  }

  public void wrap(WorkflowInstanceRecord record) {
    elementIdProp.setValue(record.getElementIdBuffer());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    flowScopeKeyProp.setValue(record.getFlowScopeKey());
    versionProp.setValue(record.getVersion());
    workflowKeyProp.setValue(record.getWorkflowKey());
    workflowInstanceKeyProp.setValue(record.getWorkflowInstanceKey());
    bpmnElementTypeProp.setValue(record.getBpmnElementType());
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

  public WorkflowInstanceRecord setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
    return this;
  }

  public WorkflowInstanceRecord setBpmnProcessId(
      DirectBuffer directBuffer, int offset, int length) {
    bpmnProcessIdProp.setValue(directBuffer, offset, length);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public long getFlowScopeKey() {
    return flowScopeKeyProp.getValue();
  }

  public BpmnElementType getBpmnElementType() {
    return bpmnElementTypeProp.getValue();
  }

  public WorkflowInstanceRecord setBpmnElementType(BpmnElementType bpmnType) {
    bpmnElementTypeProp.setValue(bpmnType);
    return this;
  }

  public WorkflowInstanceRecord setFlowScopeKey(long flowScopeKey) {
    this.flowScopeKeyProp.setValue(flowScopeKey);
    return this;
  }

  public WorkflowInstanceRecord setElementId(String elementId) {
    this.elementIdProp.setValue(elementId);
    return this;
  }

  public WorkflowInstanceRecord setElementId(DirectBuffer elementId) {
    return setElementId(elementId, 0, elementId.capacity());
  }

  public WorkflowInstanceRecord setWorkflowKey(long workflowKey) {
    this.workflowKeyProp.setValue(workflowKey);
    return this;
  }

  public WorkflowInstanceRecord setVersion(int version) {
    this.versionProp.setValue(version);
    return this;
  }

  public WorkflowInstanceRecord setBpmnProcessId(String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public WorkflowInstanceRecord setBpmnProcessId(DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer);
    return this;
  }

  public WorkflowInstanceRecord setElementId(DirectBuffer elementId, int offset, int length) {
    this.elementIdProp.setValue(elementId, offset, length);
    return this;
  }
}
