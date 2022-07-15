/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationVariableInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* 'encodedLength' is a technical field needed for MsgPack and inherited from ObjectValue; it has
  no purpose in exported JSON records*/
  "encodedLength"
})
public final class ProcessInstanceModificationVariableInstruction extends ObjectValue
    implements ProcessInstanceModificationVariableInstructionValue {

  private final DocumentProperty variablesProp = new DocumentProperty("variables");
  private final StringProperty elementIdProp = new StringProperty("elementId", "");

  public ProcessInstanceModificationVariableInstruction() {
    declareProperty(variablesProp).declareProperty(elementIdProp);
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(getVariablesBuffer());
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(getElementIdBuffer());
  }

  public ProcessInstanceModificationVariableInstruction setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public ProcessInstanceModificationVariableInstruction setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  public void copy(final ProcessInstanceModificationVariableInstruction object) {
    setVariables(object.getVariablesBuffer());
    setElementId(object.getElementId());
  }

  /** hashCode relies on implementation provided by {@link ObjectValue#hashCode()} */
  @Override
  public int hashCode() {
    return super.hashCode();
  }

  /** equals relies on implementation provided by {@link ObjectValue#equals(Object)} */
  @Override
  public boolean equals(final Object o) {
    return super.equals(o);
  }
}
