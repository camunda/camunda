/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.AsyncRequestRecordValue;
import java.util.stream.Stream;

public class AsyncRequestRecordStream
    extends ExporterRecordStream<AsyncRequestRecordValue, AsyncRequestRecordStream> {

  public AsyncRequestRecordStream(final Stream<Record<AsyncRequestRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected AsyncRequestRecordStream supply(
      final Stream<Record<AsyncRequestRecordValue>> wrappedStream) {
    return new AsyncRequestRecordStream(wrappedStream);
  }

  public AsyncRequestRecordStream withRequestScopeKey(final long scopeKey) {
    return valueFilter(v -> v.getScopeKey() == scopeKey);
  }

  public AsyncRequestRecordStream withRequestValueType(final ValueType valueType) {
    return valueFilter(v -> v.getValueType() == valueType);
  }

  public AsyncRequestRecordStream withRequestIntent(final Intent intent) {
    return valueFilter(v -> v.getIntent() == intent);
  }
}
