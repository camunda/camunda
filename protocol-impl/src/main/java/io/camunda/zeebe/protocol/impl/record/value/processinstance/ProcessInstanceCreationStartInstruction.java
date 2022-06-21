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
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationStartInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({"encodedLength"})
public class ProcessInstanceCreationStartInstruction extends ObjectValue
    implements ProcessInstanceCreationStartInstructionValue {

  private final StringProperty elementIdProp = new StringProperty("elementId");

  public ProcessInstanceCreationStartInstruction() {
    declareProperty(elementIdProp);
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public ProcessInstanceCreationStartInstruction setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public void copy(final ProcessInstanceCreationStartInstruction startInstruction) {
    setElementId(startInstruction.getElementId());
  }
}
