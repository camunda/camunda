/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
      new ObjectProperty<>("commandValue", new UnifiedRecordValue(10));

  private final EnumProperty<ValueType> valueTypeFollowupProperty =
      new EnumProperty<>("valueTypeFollowup", ValueType.class);
  private final IntegerProperty intentFollowupProperty =
      new IntegerProperty("intentFollowup", Intent.NULL_VAL);
  private final ObjectProperty<UnifiedRecordValue> commandValueFollowupProperty =
      new ObjectProperty<>("commandValueFollowup", new UnifiedRecordValue(10));

  public PersistedCommandDistribution() {
    super(6);
    declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(commandValueProperty)
        .declareProperty(valueTypeFollowupProperty)
        .declareProperty(intentFollowupProperty)
        .declareProperty(commandValueFollowupProperty);
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

    valueTypeFollowupProperty.setValue(commandDistributionRecord.getValueTypeForFollowup());
    intentFollowupProperty.setValue(commandDistributionRecord.getIntentForFollowup().value());

    final var commandValueFollowup = commandDistributionRecord.getCommandValueForFollowup();
    if (commandValueFollowup == null) {
      return this;
    }
    final var valueBufferFollowup = new UnsafeBuffer(0, 0);
    final int encodedLengthFollowup = commandValueFollowup.getLength();
    valueBufferFollowup.wrap(new byte[encodedLengthFollowup]);
    commandValueFollowup.write(valueBufferFollowup, 0);
    commandValueFollowupProperty.getValue().wrap(valueBufferFollowup, 0, encodedLengthFollowup);

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

  public ValueType getValueTypeForFollowup() {
    return valueTypeFollowupProperty.getValue();
  }

  public Intent getIntentForFollowup() {
    final int intentValue = intentFollowupProperty.getValue();
    if (intentValue < 0 || intentValue > Short.MAX_VALUE) {
      throw new IllegalStateException(
          String.format(
              "Expected to read the intent, but it's persisted value '%d' is not a short integer",
              intentValue));
    }
    return Intent.fromProtocolValue(getValueTypeForFollowup(), (short) intentValue);
  }

  public UnifiedRecordValue getCommandValueForFollowup() {
    return commandValueFollowupProperty.getValue();
  }
}
