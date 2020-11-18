/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.record.value.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class VariableRecord extends UnifiedRecordValue implements VariableRecordValue {

  private final StringProperty nameProp = new StringProperty("name");
  private final BinaryProperty valueProp = new BinaryProperty("value");
  private final LongProperty scopeKeyProp = new LongProperty("scopeKey");
  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey");
  private final LongProperty workflowKeyProp = new LongProperty("workflowKey");

  public VariableRecord() {
    declareProperty(nameProp)
        .declareProperty(valueProp)
        .declareProperty(scopeKeyProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(workflowKeyProp);
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  @Override
  public String getValue() {
    return MsgPackConverter.convertToJson(valueProp.getValue());
  }

  public long getScopeKey() {
    return scopeKeyProp.getValue();
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public VariableRecord setWorkflowKey(final long workflowKey) {
    workflowKeyProp.setValue(workflowKey);
    return this;
  }

  public VariableRecord setScopeKey(final long scopeKey) {
    scopeKeyProp.setValue(scopeKey);
    return this;
  }

  public VariableRecord setValue(final DirectBuffer value) {
    valueProp.setValue(value);
    return this;
  }

  public VariableRecord setName(final DirectBuffer name) {
    nameProp.setValue(name);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getValueBuffer() {
    return valueProp.getValue();
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public VariableRecord setWorkflowInstanceKey(final long workflowInstanceKey) {
    workflowInstanceKeyProp.setValue(workflowInstanceKey);
    return this;
  }
}
