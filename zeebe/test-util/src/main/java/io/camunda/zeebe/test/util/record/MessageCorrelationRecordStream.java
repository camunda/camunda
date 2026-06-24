/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import java.util.stream.Stream;

public final class MessageCorrelationRecordStream
    extends ExporterRecordStream<MessageCorrelationRecordValue, MessageCorrelationRecordStream> {

  public MessageCorrelationRecordStream(
      final Stream<Record<MessageCorrelationRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MessageCorrelationRecordStream supply(
      final Stream<Record<MessageCorrelationRecordValue>> wrappedStream) {
    return new MessageCorrelationRecordStream(wrappedStream);
  }

  public MessageCorrelationRecordStream withName(final String name) {
    return valueFilter(v -> name.equals(v.getName()));
  }

  public MessageCorrelationRecordStream withCorrelationKey(final String correlationKey) {
    return valueFilter(v -> correlationKey.equals(v.getCorrelationKey()));
  }
}
