/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import java.util.stream.Stream;

public final class MessageRecordStream
    extends ExporterRecordWithVariablesStream<MessageRecordValue, MessageRecordStream> {

  public MessageRecordStream(final Stream<Record<MessageRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MessageRecordStream supply(final Stream<Record<MessageRecordValue>> wrappedStream) {
    return new MessageRecordStream(wrappedStream);
  }

  public MessageRecordStream withName(final String name) {
    return valueFilter(v -> name.equals(v.getName()));
  }

  public MessageRecordStream withCorrelationKey(final String correlationKey) {
    return valueFilter(v -> correlationKey.equals(v.getCorrelationKey()));
  }

  public MessageRecordStream withTenantId(final String tenantId) {
    return valueFilter(v -> tenantId.equals(v.getTenantId()));
  }

  public MessageRecordStream withMessageId(final String messageId) {
    return valueFilter(v -> messageId.equals(v.getMessageId()));
  }

  public MessageRecordStream withTimeToLive(final long timeToLive) {
    return valueFilter(v -> v.getTimeToLive() == timeToLive);
  }
}
