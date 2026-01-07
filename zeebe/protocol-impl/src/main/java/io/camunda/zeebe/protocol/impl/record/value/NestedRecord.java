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
  private static final StringValue RECORD_VALUE_KEY = new StringValue("recordValue");

  /**
   * NOTE! When adding a new property here it must also be added to the ProtocolFactor! This class
   * contains a randomizer implementation which is used to generate a random NestedRecord. The new
   * property must be added there. Without it we won't generate a complete record.
   */
  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>(VALUE_TYPE_KEY, ValueType.class, ValueType.NULL_VAL);

  private final IntegerProperty intentProperty = new IntegerProperty(INTENT_KEY, Intent.NULL_VAL);
  private final ObjectProperty<UnifiedRecordValue> recordValueProperty =
      new ObjectProperty<>(RECORD_VALUE_KEY, new UnifiedRecordValue(10));

  private final MsgPackWriter recordValueWriter = new MsgPackWriter();
  private final MsgPackReader recordValueReader = new MsgPackReader();

  public NestedRecord() {
    super(3);
    declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(recordValueProperty);
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

    final var storedRecordValue = recordValueProperty.getValue();
    if (storedRecordValue.isEmpty()) {
      return storedRecordValue;
    }

    final var recordValue = UnifiedRecordValue.fromValueType(valueType);
    if (recordValue == null) {
      throw new IllegalStateException(
          "Expected to read the record value, but it's type `"
              + valueType.name()
              + "` is unknown. Please add it to UnifiedRecordValue#fromValueType.");
    }

    // write the record value property's content into a buffer
    final var recordValueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = storedRecordValue.getEncodedLength();
    recordValueBuffer.wrap(new byte[encodedLength]);
    storedRecordValue.write(recordValueWriter.wrap(recordValueBuffer, 0));

    // read the value back from the buffer into the concrete record value
    recordValue.wrap(recordValueBuffer);
    return recordValue;
  }

  public NestedRecord setRecordValue(final UnifiedRecordValue recordValue) {
    if (recordValue == null) {
      recordValueProperty.reset();
      return this;
    }

    // inspired by IndexedRecord.setValue
    final var valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = recordValue.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    recordValue.write(valueBuffer, 0);
    recordValueProperty.getValue().read(recordValueReader.wrap(valueBuffer, 0, encodedLength));
    return this;
  }

  public NestedRecord setIntent(final Intent intent) {
    intentProperty.setValue(intent.value());
    return this;
  }

  public NestedRecord setValueType(final ValueType valueType) {
    valueTypeProperty.setValue(valueType);
    return this;
  }

  public NestedRecord wrap(final NestedRecord nestedRecord) {
    setValueType(nestedRecord.getValueType());
    setIntent(nestedRecord.getIntent());
    setRecordValue(nestedRecord.getRecordValue());
    return this;
  }
}
