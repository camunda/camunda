/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageBatchRecordValue;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class MessageBatchRecord extends UnifiedRecordValue
    implements MessageBatchRecordValue {

  private final ArrayProperty<LongValue> messageKeysProp =
      new ArrayProperty<>("messageKeys", LongValue::new);

  public MessageBatchRecord() {
    super(1);
    declareProperty(messageKeysProp);
  }

  public ValueArray<LongValue> messageKeys() {
    return messageKeysProp;
  }

  @Override
  public boolean isEmpty() {
    return messageKeysProp.isEmpty();
  }

  public MessageBatchRecord addMessageKey(final long key) {
    messageKeys().add().setValue(key);
    return this;
  }

  @Override
  public List<Long> getMessageKeys() {
    return StreamSupport.stream(messageKeysProp.spliterator(), false)
        .map(LongValue::getValue)
        .collect(Collectors.toList());
  }
}
