/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Routes a record to the handlers registered for it: every typed handler whose value class matches,
 * followed by the catch-all handler if one is set.
 */
final class RecordDispatcher {

  private final List<TypedHandler<?>> typedHandlers = new ArrayList<>();
  private Consumer<Record<?>> catchAll;

  <V extends RecordValue> void on(
      final Class<V> type, final BiConsumer<Record<?>, ? super V> handler) {
    typedHandlers.add(new TypedHandler<>(type, handler));
  }

  void onAny(final Consumer<Record<?>> handler) {
    catchAll = handler;
  }

  void dispatch(final Record<?> record) {
    final RecordValue value = record.getValue();
    for (final TypedHandler<?> handler : typedHandlers) {
      handler.accept(record, value);
    }
    if (catchAll != null) {
      catchAll.accept(record);
    }
  }

  /** A handler bound to a record value type; invoked only for records whose value matches. */
  private record TypedHandler<V extends RecordValue>(
      Class<V> type, BiConsumer<Record<?>, ? super V> handler) {

    void accept(final Record<?> record, final RecordValue value) {
      if (type.isInstance(value)) {
        handler.accept(record, type.cast(value));
      }
    }
  }
}
