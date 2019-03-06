/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.protocol.impl.record.value.workflowinstance;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.WorkflowInstanceRelated;
import org.agrona.DirectBuffer;

public class WorkflowInstanceRecord extends UnpackedObject implements WorkflowInstanceRelated {

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

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public WorkflowInstanceRecord setBpmnProcessId(String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public WorkflowInstanceRecord setBpmnProcessId(DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer);
    return this;
  }

  public WorkflowInstanceRecord setBpmnProcessId(
      DirectBuffer directBuffer, int offset, int length) {
    bpmnProcessIdProp.setValue(directBuffer, offset, length);
    return this;
  }

  public DirectBuffer getElementId() {
    return elementIdProp.getValue();
  }

  public WorkflowInstanceRecord setElementId(String elementId) {
    this.elementIdProp.setValue(elementId);
    return this;
  }

  public WorkflowInstanceRecord setElementId(DirectBuffer elementId) {
    return setElementId(elementId, 0, elementId.capacity());
  }

  public WorkflowInstanceRecord setElementId(DirectBuffer elementId, int offset, int length) {
    this.elementIdProp.setValue(elementId, offset, length);
    return this;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public WorkflowInstanceRecord setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
    return this;
  }

  public long getFlowScopeKey() {
    return flowScopeKeyProp.getValue();
  }

  public WorkflowInstanceRecord setFlowScopeKey(long flowScopeKey) {
    this.flowScopeKeyProp.setValue(flowScopeKey);
    return this;
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public WorkflowInstanceRecord setVersion(int version) {
    this.versionProp.setValue(version);
    return this;
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public WorkflowInstanceRecord setWorkflowKey(long workflowKey) {
    this.workflowKeyProp.setValue(workflowKey);
    return this;
  }

  public BpmnElementType getBpmnElementType() {
    return bpmnElementTypeProp.getValue();
  }

  public WorkflowInstanceRecord setBpmnElementType(BpmnElementType bpmnType) {
    bpmnElementTypeProp.setValue(bpmnType);
    return this;
  }

  public void wrap(WorkflowInstanceRecord record) {
    elementIdProp.setValue(record.getElementId());
    bpmnProcessIdProp.setValue(record.getBpmnProcessId());
    flowScopeKeyProp.setValue(record.getFlowScopeKey());
    versionProp.setValue(record.getVersion());
    workflowKeyProp.setValue(record.getWorkflowKey());
    workflowInstanceKeyProp.setValue(record.getWorkflowInstanceKey());
    bpmnElementTypeProp.setValue(record.getBpmnElementType());
  }
}
