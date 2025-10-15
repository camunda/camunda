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
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;

public class PersistedCommandDistribution extends UnpackedObject implements DbValue {

  private final StringProperty queueIdProperty = new StringProperty("queueId", "");
  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>("valueType", ValueType.class);
  private final IntegerProperty intentProperty = new IntegerProperty("intent", Intent.NULL_VAL);
  private final ObjectProperty<UnifiedRecordValue> commandValueProperty =
      new ObjectProperty<>("commandValue", new UnifiedRecordValue(10));
  private final ObjectProperty<AuthInfo> authInfoProperty =
      new ObjectProperty<>("authInfo", new AuthInfo());

  public PersistedCommandDistribution() {
    super(5);
    declareProperty(queueIdProperty)
        .declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(commandValueProperty)
        .declareProperty(authInfoProperty);
  }

  public PersistedCommandDistribution wrap(
      final CommandDistributionRecord commandDistributionRecord) {
    if (commandDistributionRecord.getQueueId() != null) {
      queueIdProperty.setValue(commandDistributionRecord.getQueueId());
    } else {
      queueIdProperty.reset();
    }
    valueTypeProperty.setValue(commandDistributionRecord.getValueType());
    intentProperty.setValue(commandDistributionRecord.getIntent().value());

    commandValueProperty.getValue().copyFrom(commandDistributionRecord.getCommandValue());
    authInfoProperty.getValue().copyFrom(commandDistributionRecord.getAuthInfo());

    return this;
  }

  public Optional<String> getQueueId() {
    final var value = BufferUtil.bufferAsString(queueIdProperty.getValue());
    return value.isEmpty() ? Optional.empty() : Optional.of(value);
  }

  public PersistedCommandDistribution setQueueId(final String queueId) {
    queueIdProperty.setValue(queueId);
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

  public AuthInfo getAuthInfo() {
    return authInfoProperty.getValue();
  }
}
