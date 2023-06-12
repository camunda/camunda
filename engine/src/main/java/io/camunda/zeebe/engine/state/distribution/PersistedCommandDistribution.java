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
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import org.agrona.concurrent.UnsafeBuffer;

public class PersistedCommandDistribution extends UnpackedObject implements DbValue {

  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>("valueType", ValueType.class);
  private final IntegerProperty intentProperty = new IntegerProperty("intent", Intent.NULL_VAL);
  private final ObjectProperty<UnifiedRecordValue> commandValueProperty =
      new ObjectProperty<>("commandValue", new UnifiedRecordValue());

  public PersistedCommandDistribution() {
    declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(commandValueProperty);
  }

  public PersistedCommandDistribution wrap(
      final CommandDistributionRecord commandDistributionRecord) {
    valueTypeProperty.setValue(commandDistributionRecord.getValueType());
    intentProperty.setValue(commandDistributionRecord.getIntent().value());

    final var commandValue = commandDistributionRecord.getCommandValue();
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

  public Intent getIntent() {
    final int intentValue = intentProperty.getValue();
    if (intentValue < 0 || intentValue > Short.MAX_VALUE) {
      throw new IllegalStateException(
          String.format(
              "Expected to read the intent, but it's persisted value '%d' is not a short integer",
              intentValue));
    }
    return Intent.fromProtocolValue(getValueType(), (short) intentValue);
  }

  public UnifiedRecordValue getCommandValue() {
    return commandValueProperty.getValue();
  }
}
