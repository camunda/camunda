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
package io.zeebe.protocol.impl.record.value.job;

import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_INSTANCE_KEY;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class JobHeaders extends UnpackedObject {
  private static final String EMPTY_STRING = "";

  private final LongProperty workflowInstanceKeyProp =
      new LongProperty(PROP_WORKFLOW_INSTANCE_KEY, -1L);
  private final StringProperty bpmnProcessIdProp =
      new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID, EMPTY_STRING);
  private final IntegerProperty workflowDefinitionVersionProp =
      new IntegerProperty("workflowDefinitionVersion", -1);
  private final LongProperty workflowKeyProp = new LongProperty("workflowKey", -1L);
  private final StringProperty elementIdProp = new StringProperty("elementId", EMPTY_STRING);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);

  public JobHeaders() {
    this.declareProperty(bpmnProcessIdProp)
        .declareProperty(workflowDefinitionVersionProp)
        .declareProperty(workflowKeyProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp);
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public JobHeaders setWorkflowInstanceKey(long key) {
    this.workflowInstanceKeyProp.setValue(key);
    return this;
  }

  public DirectBuffer getElementId() {
    return elementIdProp.getValue();
  }

  public JobHeaders setElementId(String elementId) {
    this.elementIdProp.setValue(elementId);
    return this;
  }

  public JobHeaders setElementId(DirectBuffer elementId) {
    return setElementId(elementId, 0, elementId.capacity());
  }

  public JobHeaders setElementId(DirectBuffer activityId, int offset, int length) {
    this.elementIdProp.setValue(activityId, offset, length);
    return this;
  }

  public JobHeaders setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public JobHeaders setBpmnProcessId(DirectBuffer bpmnProcessId) {
    this.bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public int getWorkflowDefinitionVersion() {
    return workflowDefinitionVersionProp.getValue();
  }

  public JobHeaders setWorkflowDefinitionVersion(int version) {
    this.workflowDefinitionVersionProp.setValue(version);
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public JobHeaders setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public JobHeaders setWorkflowKey(long workflowKey) {
    this.workflowKeyProp.setValue(workflowKey);
    return this;
  }
}
