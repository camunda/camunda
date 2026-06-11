/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Routes records to handlers by (ValueType, Intent) pairs. Supports multiple handlers per
 * ValueType, each keyed by a distinct Intent. Two-level lookup: EnumMap for ValueType, then HashMap
 * for Intent.
 *
 * <p>On {@link #apply}, installs an {@link AnalyticsRecordFilter} derived from the registered types
 * and intents.
 */
final class HandlerRegistry {

  private final EnumMap<ValueType, Map<Intent, AnalyticsHandler<?>>> handlers =
      new EnumMap<>(ValueType.class);

  <T extends RecordValue> HandlerRegistry register(
      final ValueType valueType, final Intent intent, final AnalyticsHandler<T> handler) {
    final var intentMap = handlers.computeIfAbsent(valueType, k -> new HashMap<>());
    if (intentMap.containsKey(intent)) {
      throw new IllegalStateException("Duplicate handler for (" + valueType + ", " + intent + ")");
    }
    intentMap.put(intent, handler);
    return this;
  }

  HandlerRegistry apply(final Context context) {
    final var acceptedIntents =
        handlers.values().stream()
            .flatMap(m -> m.keySet().stream())
            .collect(Collectors.toUnmodifiableSet());
    context.setFilter(
        new AnalyticsRecordFilter(handlers.keySet(), acceptedIntents, context.getPartitionId()));
    return this;
  }

  /** Returns all registered (ValueType, Intent) pairs. Package-private for use in tests. */
  Set<Map.Entry<ValueType, Intent>> registrations() {
    return handlers.entrySet().stream()
        .flatMap(e -> e.getValue().keySet().stream().map(intent -> Map.entry(e.getKey(), intent)))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Routes the record to its handler. Does nothing if no handler is registered. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  void handle(final Record<?> record) {
    final var intentMap = handlers.get(record.getValueType());
    if (intentMap == null) {
      return;
    }
    final AnalyticsHandler handler = intentMap.get(record.getIntent());
    if (handler == null) {
      return;
    }
    handler.handle(record);
  }
}
