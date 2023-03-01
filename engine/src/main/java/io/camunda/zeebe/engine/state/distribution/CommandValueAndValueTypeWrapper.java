/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.distribution;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The CommandValueAndValueTypeWrapper is used to store a command value with it's corresponding
 * ValueType in the state.
 */
public class CommandValueAndValueTypeWrapper extends UnpackedObject implements DbValue {

  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>("valueType", ValueType.class);
  private final ObjectProperty<UnifiedRecordValue> commandValueProperty =
      new ObjectProperty<>("commandValue", new UnifiedRecordValue());

  public CommandValueAndValueTypeWrapper() {
    declareProperty(valueTypeProperty).declareProperty(commandValueProperty);
  }

  public CommandValueAndValueTypeWrapper wrap(
      final CommandDistributionRecord commandDistributionRecord) {
    valueTypeProperty.setValue(commandDistributionRecord.getValueType());

    final var commandValue = (UnifiedRecordValue) commandDistributionRecord.getCommandValue();
    final var valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = commandValue.getLength();
    valueBuffer.wrap(new byte[encodedLength]);
    commandValue.write(valueBuffer, 0);
    commandValueProperty.getValue().wrap(valueBuffer, 0, encodedLength);

    return this;
  }

  public ValueType getValueType() {
    return valueTypeProperty.getValue();
  }

  public UnifiedRecordValue getCommandValue() {
    return commandValueProperty.getValue();
  }
}
