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
package io.zeebe.protocol.impl.record.value.timer;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.WorkflowInstanceRelated;
import org.agrona.DirectBuffer;

public class TimerRecord extends UnpackedObject implements WorkflowInstanceRelated {

  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey");
  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey");
  private final LongProperty dueDateProp = new LongProperty("dueDate");
  private final StringProperty handlerNodeId = new StringProperty("handlerNodeId");
  private final IntegerProperty repetitionsProp = new IntegerProperty("repetitions");
  private final LongProperty workflowKeyProp = new LongProperty("workflowKey");

  public TimerRecord() {
    this.declareProperty(elementInstanceKeyProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(dueDateProp)
        .declareProperty(handlerNodeId)
        .declareProperty(repetitionsProp)
        .declareProperty(workflowKeyProp);
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public TimerRecord setElementInstanceKey(long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  public long getDueDate() {
    return dueDateProp.getValue();
  }

  public TimerRecord setDueDate(long dueDate) {
    this.dueDateProp.setValue(dueDate);
    return this;
  }

  public DirectBuffer getHandlerNodeId() {
    return handlerNodeId.getValue();
  }

  public TimerRecord setHandlerNodeId(DirectBuffer handlerNodeId) {
    this.handlerNodeId.setValue(handlerNodeId);
    return this;
  }

  public int getRepetitions() {
    return repetitionsProp.getValue();
  }

  public TimerRecord setRepetitions(int repetitions) {
    this.repetitionsProp.setValue(repetitions);
    return this;
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public TimerRecord setWorkflowKey(long workflowKey) {
    this.workflowKeyProp.setValue(workflowKey);
    return this;
  }

  public TimerRecord setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
    return this;
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }
}
