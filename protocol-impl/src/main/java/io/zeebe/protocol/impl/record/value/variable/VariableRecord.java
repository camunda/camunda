/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey");
  private final LongProperty processDefinitionKeyProp = new LongProperty("processDefinitionKey");

  public VariableRecord() {
    declareProperty(nameProp)
        .declareProperty(valueProp)
        .declareProperty(scopeKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(processDefinitionKeyProp);
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

  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public VariableRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
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

  public VariableRecord setValue(final DirectBuffer value, final int offset, final int length) {
    valueProp.setValue(value, offset, length);
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

  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public VariableRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }
}
