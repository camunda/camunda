/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.test.util.stream.StreamWrapper;
import java.util.stream.Stream;

public final class TypedRecordStream<T extends UnifiedRecordValue>
    extends StreamWrapper<Record<T>, TypedRecordStream<T>> {

  public TypedRecordStream(final Stream<Record<T>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected TypedRecordStream<T> supply(final Stream<Record<T>> wrappedStream) {
    return new TypedRecordStream<>(wrappedStream);
  }

  public TypedRecordStream<T> onlyCommands() {
    return new TypedRecordStream<>(filter(r -> r.getRecordType() == RecordType.COMMAND));
  }

  public TypedRecordStream<T> onlyEvents() {
    return new TypedRecordStream<>(filter(r -> r.getRecordType() == RecordType.EVENT));
  }

  public TypedRecordStream<T> onlyRejections() {
    return new TypedRecordStream<>(filter(r -> r.getRecordType() == RecordType.COMMAND_REJECTION));
  }

  public TypedRecordStream<T> withIntent(final Intent intent) {
    return new TypedRecordStream<>(filter(r -> r.getIntent() == intent));
  }
}
