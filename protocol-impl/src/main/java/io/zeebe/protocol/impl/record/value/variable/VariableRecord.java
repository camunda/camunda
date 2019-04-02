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
package io.zeebe.protocol.impl.record.value.variable;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.WorkflowInstanceRelated;
import org.agrona.DirectBuffer;

public class VariableRecord extends UnpackedObject implements WorkflowInstanceRelated {

  private final StringProperty nameProp = new StringProperty("name");
  private final BinaryProperty valueProp = new BinaryProperty("value");
  private final LongProperty scopeKeyProp = new LongProperty("scopeKey");
  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey");
  private final LongProperty workflowKeyProp = new LongProperty("workflowKey");

  public VariableRecord() {
    this.declareProperty(nameProp)
        .declareProperty(valueProp)
        .declareProperty(scopeKeyProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(workflowKeyProp);
  }

  public DirectBuffer getName() {
    return nameProp.getValue();
  }

  public VariableRecord setName(DirectBuffer name) {
    this.nameProp.setValue(name);
    return this;
  }

  public DirectBuffer getValue() {
    return valueProp.getValue();
  }

  public VariableRecord setValue(DirectBuffer value) {
    this.valueProp.setValue(value);
    return this;
  }

  public long getScopeKey() {
    return scopeKeyProp.getValue();
  }

  public VariableRecord setScopeKey(long scopeKey) {
    this.scopeKeyProp.setValue(scopeKey);
    return this;
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public VariableRecord setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
    return this;
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public VariableRecord setWorkflowKey(long workflowKey) {
    this.workflowKeyProp.setValue(workflowKey);
    return this;
  }
}
