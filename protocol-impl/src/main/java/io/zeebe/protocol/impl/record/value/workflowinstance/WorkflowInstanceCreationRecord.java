/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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

  public WorkflowInstanceCreationRecord setWorkflowKey(long key) {
    this.workflowKeyProperty.setValue(key);
    return this;
  }

  public WorkflowInstanceCreationRecord setVersion(int version) {
    this.versionProperty.setValue(version);
    return this;
  }

  public WorkflowInstanceCreationRecord setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  public WorkflowInstanceCreationRecord setBpmnProcessId(DirectBuffer bpmnProcessId) {
    this.bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProperty.getValue();
  }

  public WorkflowInstanceCreationRecord setWorkflowInstanceKey(long instanceKey) {
    this.workflowInstanceKeyProperty.setValue(instanceKey);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public WorkflowInstanceCreationRecord setVariables(DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProperty.getValue();
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }
}
