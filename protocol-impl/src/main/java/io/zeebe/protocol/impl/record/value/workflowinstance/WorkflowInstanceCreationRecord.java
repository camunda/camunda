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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.intent.WorkflowInstanceRelated;
import io.zeebe.protocol.record.value.WorkflowInstanceCreationRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public class WorkflowInstanceCreationRecord extends UnifiedRecordValue
    implements WorkflowInstanceRelated, WorkflowInstanceCreationRecordValue {

  private final StringProperty bpmnProcessIdProperty = new StringProperty("bpmnProcessId", "");
  private final LongProperty keyProperty = new LongProperty("key", -1);
  private final IntegerProperty versionProperty = new IntegerProperty("version", -1);
  private final DocumentProperty variablesProperty = new DocumentProperty("variables");
  private final LongProperty instanceKeyProperty = new LongProperty("instanceKey", -1);

  public WorkflowInstanceCreationRecord() {
    this.declareProperty(bpmnProcessIdProperty)
        .declareProperty(keyProperty)
        .declareProperty(instanceKeyProperty)
        .declareProperty(versionProperty)
        .declareProperty(variablesProperty);
  }

  public WorkflowInstanceCreationRecord setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  public WorkflowInstanceCreationRecord setBpmnProcessId(DirectBuffer bpmnProcessId) {
    this.bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProperty.getValue();
  }

  public WorkflowInstanceCreationRecord setKey(long key) {
    this.keyProperty.setValue(key);
    return this;
  }

  public long getKey() {
    return keyProperty.getValue();
  }

  public WorkflowInstanceCreationRecord setVersion(int version) {
    this.versionProperty.setValue(version);
    return this;
  }

  public WorkflowInstanceCreationRecord setInstanceKey(long instanceKey) {
    this.instanceKeyProperty.setValue(instanceKey);
    return this;
  }

  public long getInstanceKey() {
    return instanceKeyProperty.getValue();
  }

  public int getVersion() {
    return versionProperty.getValue();
  }

  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }

  public WorkflowInstanceCreationRecord setVariables(DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }

  @Override
  @JsonIgnore
  public long getWorkflowInstanceKey() {
    return getInstanceKey();
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProperty.getValue());
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  @Override
  public String toJson() {
    return MsgPackConverter.convertRecordToJson(this);
  }
}
