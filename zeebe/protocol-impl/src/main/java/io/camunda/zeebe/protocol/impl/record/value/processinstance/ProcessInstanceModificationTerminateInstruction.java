/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationTerminateInstructionValue;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class ProcessInstanceModificationTerminateInstruction extends ObjectValue
    implements ProcessInstanceModificationTerminateInstructionValue {

  private final LongProperty elementInstanceKeyProperty = new LongProperty("elementInstanceKey");

  public ProcessInstanceModificationTerminateInstruction() {
    super(1);
    declareProperty(elementInstanceKeyProperty);
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProperty.getValue();
  }

  public ProcessInstanceModificationTerminateInstruction setElementInstanceKey(
      final long elementInstanceKey) {
    elementInstanceKeyProperty.setValue(elementInstanceKey);
    return this;
  }

  public void copy(final ProcessInstanceModificationTerminateInstructionValue object) {
    setElementInstanceKey(object.getElementInstanceKey());
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
