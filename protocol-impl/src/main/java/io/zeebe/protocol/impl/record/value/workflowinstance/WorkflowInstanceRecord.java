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
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.spec.MsgPackHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class WorkflowInstanceRecord extends UnpackedObject {
  public static final DirectBuffer EMPTY_PAYLOAD = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

  public static final String PROP_WORKFLOW_BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String PROP_WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";
  public static final String PROP_WORKFLOW_ELEMENT_ID = "elementId";
  public static final String PROP_WORKFLOW_VERSION = "version";
  public static final String PROP_WORKFLOW_KEY = "workflowKey";
  public static final String PROP_WORKFLOW_PAYLOAD = "payload";

  private final StringProperty bpmnProcessIdProp =
      new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID, "");
  private final IntegerProperty versionProp = new IntegerProperty(PROP_WORKFLOW_VERSION, -1);
  private final LongProperty workflowKeyProp = new LongProperty(PROP_WORKFLOW_KEY, -1L);

  private final LongProperty workflowInstanceKeyProp =
      new LongProperty(PROP_WORKFLOW_INSTANCE_KEY, -1L);
  private final StringProperty elementIdProp = new StringProperty(PROP_WORKFLOW_ELEMENT_ID, "");

  private final DocumentProperty payloadProp = new DocumentProperty(PROP_WORKFLOW_PAYLOAD);

  private final LongProperty scopeInstanceKey = new LongProperty("scopeInstanceKey", -1L);

  public WorkflowInstanceRecord() {
    this.declareProperty(bpmnProcessIdProp)
        .declareProperty(versionProp)
        .declareProperty(workflowKeyProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(payloadProp)
        .declareProperty(scopeInstanceKey);
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

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public WorkflowInstanceRecord setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
    return this;
  }

  public long getScopeInstanceKey() {
    return scopeInstanceKey.getValue();
  }

  public WorkflowInstanceRecord setScopeInstanceKey(long scopeInstanceKey) {
    this.scopeInstanceKey.setValue(scopeInstanceKey);
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

  public DirectBuffer getPayload() {
    return payloadProp.getValue();
  }

  public WorkflowInstanceRecord setPayload(DirectBuffer payload) {
    payloadProp.setValue(payload);
    return this;
  }

  public WorkflowInstanceRecord setPayload(DirectBuffer payload, int offset, int length) {
    payloadProp.setValue(payload, offset, length);
    return this;
  }
}
