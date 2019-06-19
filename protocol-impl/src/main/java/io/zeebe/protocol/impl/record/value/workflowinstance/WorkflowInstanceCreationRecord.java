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
import io.zeebe.protocol.record.value.WorkflowInstanceCreationRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public class WorkflowInstanceCreationRecord extends UnifiedRecordValue
    implements WorkflowInstanceCreationRecordValue {

  private final StringProperty bpmnProcessIdProperty = new StringProperty("bpmnProcessId", "");
  private final LongProperty workflowKeyProperty = new LongProperty("workflowKey", -1);
  private final IntegerProperty versionProperty = new IntegerProperty("version", -1);
  private final DocumentProperty variablesProperty = new DocumentProperty("variables");
  private final LongProperty workflowInstanceKeyProperty =
      new LongProperty("workflowInstanceKey", -1);

  public WorkflowInstanceCreationRecord() {
    this.declareProperty(bpmnProcessIdProperty)
        .declareProperty(workflowKeyProperty)
        .declareProperty(workflowInstanceKeyProperty)
        .declareProperty(versionProperty)
        .declareProperty(variablesProperty);
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProperty.getValue());
  }

  public int getVersion() {
    return versionProperty.getValue();
  }

  @Override
  public long getWorkflowKey() {
    return workflowKeyProperty.getValue();
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProperty.getValue();
  }

  @Override
  @JsonIgnore
  public Map<String, Object> getVariablesAsMap() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  @Override
  public String getVariables() {
    return MsgPackConverter.convertToJson(variablesProperty.getValue());
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProperty.getValue();
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }

  public WorkflowInstanceCreationRecord setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  public WorkflowInstanceCreationRecord setBpmnProcessId(DirectBuffer bpmnProcessId) {
    this.bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  public WorkflowInstanceCreationRecord setWorkflowInstanceKey(long instanceKey) {
    this.workflowInstanceKeyProperty.setValue(instanceKey);
    return this;
  }

  public WorkflowInstanceCreationRecord setWorkflowKey(long key) {
    this.workflowKeyProperty.setValue(key);
    return this;
  }

  public WorkflowInstanceCreationRecord setVariables(DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }

  public WorkflowInstanceCreationRecord setVersion(int version) {
    this.versionProperty.setValue(version);
    return this;
  }
}
