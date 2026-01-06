/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.NestedRecordValue;
import org.agrona.concurrent.UnsafeBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; they have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class NestedRecord extends ObjectValue implements NestedRecordValue {
  private static final StringValue VALUE_TYPE_KEY = new StringValue("valueType");
  private static final StringValue INTENT_KEY = new StringValue("intent");
  private static final StringValue COMMAND_VALUE_KEY = new StringValue("commandValue");

  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>(VALUE_TYPE_KEY, ValueType.class, ValueType.NULL_VAL);
  private final IntegerProperty intentProperty = new IntegerProperty(INTENT_KEY, Intent.NULL_VAL);
  private final ObjectProperty<UnifiedRecordValue> commandValueProperty =
      new ObjectProperty<>(COMMAND_VALUE_KEY, new UnifiedRecordValue(10));

  private final MsgPackWriter commandValueWriter = new MsgPackWriter();
  private final MsgPackReader commandValueReader = new MsgPackReader();

  public NestedRecord() {
    super(3);
    declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(commandValueProperty);
  }

  @Override
  public ValueType getValueType() {
    return valueTypeProperty.getValue();
  }

  @Override
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

  @Override
  public UnifiedRecordValue getRecordValue() {
    // fetch a concrete instance of the record value by type
    final var valueType = getValueType();
    if (valueType == ValueType.NULL_VAL) {
      return null;
    }

    final var storedCommandValue = commandValueProperty.getValue();
    if (storedCommandValue.isEmpty()) {
      return storedCommandValue;
    }

    final var commandValue = UnifiedRecordValue.fromValueType(valueType);
    if (commandValue == null) {
      throw new IllegalStateException(
          "Expected to read the record value, but it's type `"
              + valueType.name()
              + "` is unknown. Please add it to UnifiedRecordValue#fromValueType.");
    }

    // write the command value property's content into a buffer
    final var commandValueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = storedCommandValue.getEncodedLength();
    commandValueBuffer.wrap(new byte[encodedLength]);
    storedCommandValue.write(commandValueWriter.wrap(commandValueBuffer, 0));

    // read the value back from the buffer into the concrete command value
    commandValue.wrap(commandValueBuffer);
    return commandValue;
  }

  public NestedRecord setIntent(final Intent intent) {
    intentProperty.setValue(intent.value());
    return this;
  }

  public NestedRecord setValueType(final ValueType valueType) {
    valueTypeProperty.setValue(valueType);
    return this;
  }

  public NestedRecord setCommandValue(final UnifiedRecordValue commandValue) {
    if (commandValue == null) {
      commandValueProperty.reset();
      return this;
    }

    // inspired by IndexedRecord.setValue
    final var valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = commandValue.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    commandValue.write(valueBuffer, 0);
    commandValueProperty.getValue().read(commandValueReader.wrap(valueBuffer, 0, encodedLength));
    return this;
  }

  public NestedRecord wrap(final NestedRecord nestedCommand) {
    setValueType(nestedCommand.getValueType());
    setIntent(nestedCommand.getIntent());
    setCommandValue(nestedCommand.getRecordValue());
    return this;
  }
}
