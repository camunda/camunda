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
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationMoveInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class ProcessInstanceModificationMoveInstruction extends ObjectValue
    implements ProcessInstanceModificationMoveInstructionValue {

  private final StringProperty sourceElementIdProperty = new StringProperty("sourceElementId");
  private final StringProperty targetElementIdProperty = new StringProperty("targetElementId");

  public ProcessInstanceModificationMoveInstruction() {
    super(4);
    declareProperty(sourceElementIdProperty).declareProperty(targetElementIdProperty);
  }

  @Override
  public String getSourceElementId() {
    return BufferUtil.bufferAsString(getSourceElementIdBuffer());
  }

  @Override
  public String getTargetElementId() {
    return BufferUtil.bufferAsString(getTargetElementIdBuffer());
  }

  public ProcessInstanceModificationMoveInstruction setTargetElementId(
      final String targetElementId) {
    targetElementIdProperty.setValue(targetElementId);
    return this;
  }

  public ProcessInstanceModificationMoveInstruction setSourceElementId(
      final String sourceElementId) {
    sourceElementIdProperty.setValue(sourceElementId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getTargetElementIdBuffer() {
    return targetElementIdProperty.getValue();
  }

  @JsonIgnore
  public DirectBuffer getSourceElementIdBuffer() {
    return sourceElementIdProperty.getValue();
  }

  public ProcessInstanceModificationMoveInstruction copy(
      final ProcessInstanceModificationMoveInstruction object) {
    setSourceElementId(object.getSourceElementId());
    setTargetElementId(object.getTargetElementId());
    return this;
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
