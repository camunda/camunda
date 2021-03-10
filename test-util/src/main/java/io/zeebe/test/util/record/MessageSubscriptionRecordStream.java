/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import java.util.stream.Stream;

public final class MessageSubscriptionRecordStream
    extends ExporterRecordStream<MessageSubscriptionRecordValue, MessageSubscriptionRecordStream> {

  public MessageSubscriptionRecordStream(
      final Stream<Record<MessageSubscriptionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MessageSubscriptionRecordStream supply(
      final Stream<Record<MessageSubscriptionRecordValue>> wrappedStream) {
    return new MessageSubscriptionRecordStream(wrappedStream);
  }

  public MessageSubscriptionRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public MessageSubscriptionRecordStream withElementInstanceKey(final long elementInstanceKey) {
    return valueFilter(v -> v.getElementInstanceKey() == elementInstanceKey);
  }

  public MessageSubscriptionRecordStream withMessageName(final String messageName) {
    return valueFilter(v -> messageName.equals(v.getMessageName()));
  }

  public MessageSubscriptionRecordStream withCorrelationKey(final String correlationKey) {
    return valueFilter(v -> correlationKey.equals(v.getCorrelationKey()));
  }
}
