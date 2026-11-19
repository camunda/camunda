/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableSourceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class VariableSourceRecord extends UnifiedRecordValue implements VariableSourceValue {

  //  public static final VariableSourceRecord API =
  //      new VariableSourceRecord().setType(VariableOperationType.API);
  //  public static final VariableSourceRecord NONE =
  //      new VariableSourceRecord().setType(VariableOperationType.UNKNOWN);

  private static final StringValue TYPE_KEY = new StringValue("type");
  private static final StringValue ELEMENT_INSTANCE_KEY_KEY = new StringValue("elementInstanceKey");
  private static final StringValue ELEMENT_ID_KEY = new StringValue("elementId");

  private final EnumProperty<VariableOperationType> typeProp =
      new EnumProperty<>(TYPE_KEY, VariableOperationType.class, VariableOperationType.UNKNOWN);
  private final LongProperty elementInstanceKeyProp =
      new LongProperty(ELEMENT_INSTANCE_KEY_KEY, -1L);
  private final StringProperty elementIdProp = new StringProperty(ELEMENT_ID_KEY, "");

  public VariableSourceRecord() {
    super(3);
    declareProperty(typeProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(elementIdProp);
  }

  @Override
  public VariableOperationType getType() {
    return Objects.requireNonNull(typeProp.getValue());
  }

  public VariableSourceRecord setType(final VariableOperationType variableSourceType) {
    typeProp.setValue(variableSourceType);
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public VariableSourceRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public VariableSourceRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  /** Sets the element id. The record references the buffer, so it must not change afterwards. */
  public VariableSourceRecord setElementId(final DirectBuffer elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public void wrap(final VariableSourceRecord variableSourceRecord) {
    setType(variableSourceRecord.getType());
    setElementInstanceKey(variableSourceRecord.getElementInstanceKey());
    elementIdProp.setValue(variableSourceRecord.elementIdProp.getValue());
  }

  public static VariableSourceRecord api() {
    return new VariableSourceRecord().setType(VariableOperationType.API);
  }

  public static VariableSourceRecord userTaskCompletion() {
    return new VariableSourceRecord().setType(VariableOperationType.USER_TASK_COMPLETION);
  }

  public static VariableSourceRecord none() {
    return new VariableSourceRecord().setType(VariableOperationType.UNKNOWN);
  }
}
