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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationTerminateInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class ProcessInstanceModificationTerminateInstruction extends ObjectValue
    implements ProcessInstanceModificationTerminateInstructionValue {

  private final LongProperty elementInstanceKeyProperty =
      new LongProperty("elementInstanceKey", -1);
  private final StringProperty elementIdProperty = new StringProperty("elementId", "");

  public ProcessInstanceModificationTerminateInstruction() {
    super(2);
    declareProperty(elementInstanceKeyProperty).declareProperty(elementIdProperty);
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProperty.getValue();
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(getElementIdBuffer());
  }

  public ProcessInstanceModificationTerminateInstruction setElementId(final String elementId) {
    elementIdProperty.setValue(elementId);
    return this;
  }

  public ProcessInstanceModificationTerminateInstruction setElementInstanceKey(
      final long elementInstanceKey) {
    elementInstanceKeyProperty.setValue(elementInstanceKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProperty.getValue();
  }

  public void copy(final ProcessInstanceModificationTerminateInstructionValue object) {
    setElementInstanceKey(object.getElementInstanceKey());
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
