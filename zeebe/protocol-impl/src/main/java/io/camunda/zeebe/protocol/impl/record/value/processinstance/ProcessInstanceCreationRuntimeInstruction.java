/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public class ProcessInstanceCreationRuntimeInstruction extends ObjectValue
    implements ProcessInstanceCreationRuntimeInstructionValue {

  private final EnumProperty<RuntimeInstructionType> typeProp =
      new EnumProperty<>("type", RuntimeInstructionType.class);
  private final StringProperty afterElementIdProp = new StringProperty("afterElementId", "");

  public ProcessInstanceCreationRuntimeInstruction() {
    super(2);
    declareProperty(typeProp);
    declareProperty(afterElementIdProp);
  }

  /**
   * Gets the type of this instruction as an enum.
   *
   * @return the type as an enum
   */
  @Override
  public RuntimeInstructionType getType() {
    return typeProp.getValue();
  }

  public ProcessInstanceCreationRuntimeInstruction setType(final RuntimeInstructionType type) {
    typeProp.setValue(type);
    return this;
  }

  @Override
  public String getAfterElementId() {
    return BufferUtil.bufferAsString(afterElementIdProp.getValue());
  }

  public ProcessInstanceCreationRuntimeInstruction setAfterElementId(final String afterElementId) {
    afterElementIdProp.setValue(afterElementId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getAfterElementIdBuffer() {
    return afterElementIdProp.getValue();
  }

  public static ProcessInstanceCreationRuntimeInstruction createInstruction() {
    return new ProcessInstanceCreationRuntimeInstruction();
  }

  public void copy(final ProcessInstanceCreationRuntimeInstructionValue instruction) {
    setType(instruction.getType());
    setAfterElementId(instruction.getAfterElementId());
  }
}
