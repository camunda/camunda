/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.distribution;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.agrona.concurrent.UnsafeBuffer;

public final class CommandDistributionRecord extends UnifiedRecordValue
    implements CommandDistributionRecordValue {

  private static final Map<ValueType, Supplier<UnifiedRecordValue>> RECORDS_BY_TYPE =
      new EnumMap<>(ValueType.class);

  // You'll need to register any of the records value's that you want to distribute
  static {
    RECORDS_BY_TYPE.put(ValueType.DEPLOYMENT, DeploymentRecord::new);
  }

  /*
   NOTE! When adding a new property here it must also be added to the ProtocolFactory! This class
   contains a randomizer implementation which is used to generate a random
   CommandDistributionRecord. The new property must be added there. Without it we won't generate a
   complete record.
  */
  private final IntegerProperty partitionIdProperty = new IntegerProperty("partitionId");
  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>("valueType", ValueType.class, ValueType.NULL_VAL);
  private final IntegerProperty intentProperty = new IntegerProperty("intent", Intent.NULL_VAL);
  private final ObjectProperty<UnifiedRecordValue> commandValueProperty =
      new ObjectProperty<>("commandValue", new UnifiedRecordValue());
  private final MsgPackWriter recordValueWriter = new MsgPackWriter();
  private final MsgPackReader recordValueReader = new MsgPackReader();

  public CommandDistributionRecord() {
    declareProperty(partitionIdProperty)
        .declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(commandValueProperty);
  }

  @Override
  public int getPartitionId() {
    return partitionIdProperty.getValue();
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
  public UnifiedRecordValue getCommandValue() {
    // fetch a concrete instance of the record value by type
    final var valueType = getValueType();
    if (valueType == ValueType.NULL_VAL) {
      return null;
    }
    final var concrecteRecordValueSupplier = RECORDS_BY_TYPE.get(valueType);
    if (concrecteRecordValueSupplier == null) {
      throw new IllegalStateException(
          "Expected to read the record value, but it's type `"
              + valueType.name()
              + "` is unknown. Please add it to CommandDistributionRecord.RECORDS_BY_TYPE");
    }
    final var concreteRecordValue = concrecteRecordValueSupplier.get();

    // write the record value property's content into a buffer
    final var storedRecordValue = commandValueProperty.getValue();
    final var recordValueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = storedRecordValue.getEncodedLength();
    recordValueBuffer.wrap(new byte[encodedLength]);
    storedRecordValue.write(recordValueWriter.wrap(recordValueBuffer, 0));

    // read the value back from the buffer into the concrete record value
    concreteRecordValue.wrap(recordValueBuffer);
    return concreteRecordValue;
  }

  public CommandDistributionRecord setValueType(final ValueType valueType) {
    valueTypeProperty.setValue(valueType);
    return this;
  }

  public CommandDistributionRecord setPartitionId(final int partitionId) {
    partitionIdProperty.setValue(partitionId);
    return this;
  }

  public CommandDistributionRecord setIntent(final Intent intent) {
    intentProperty.setValue(intent.value());
    return this;
  }

  public CommandDistributionRecord setRecordValue(final UnifiedRecordValue recordValue) {
    // inspired by IndexedRecord.setValue
    final var valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = recordValue.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    recordValue.write(valueBuffer, 0);
    commandValueProperty.getValue().read(recordValueReader.wrap(valueBuffer, 0, encodedLength));
    return this;
  }
}
