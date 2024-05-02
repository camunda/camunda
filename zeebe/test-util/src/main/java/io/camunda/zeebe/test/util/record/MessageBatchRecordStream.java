/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.MessageBatchRecordValue;
import java.util.stream.Stream;

public final class MessageBatchRecordStream
    extends ExporterRecordStream<MessageBatchRecordValue, MessageBatchRecordStream> {

  public MessageBatchRecordStream(final Stream<Record<MessageBatchRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MessageBatchRecordStream supply(
      final Stream<Record<MessageBatchRecordValue>> wrappedStream) {
    return new MessageBatchRecordStream(wrappedStream);
  }

  public MessageBatchRecordStream hasMessageKey(final long messageKey) {
    return valueFilter(v -> v.getMessageKeys().contains(messageKey));
  }
}
