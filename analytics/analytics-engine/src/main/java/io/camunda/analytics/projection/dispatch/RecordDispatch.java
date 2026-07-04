/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.dispatch;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The two-level {@code (ValueType, Intent)} handler registry — the engine's {@code
 * onCommand(valueType, intent, handler)} shape. The registry <em>is</em> the base projection's
 * capability list: what the projection folds is read off the table, not off a chain of nested
 * type/intent branches. A record with no registered handler is ignored. A {@code ValueType} may
 * also register a wildcard handler that fires for any intent (e.g. {@code VARIABLE}, which folds
 * the same way whatever its intent).
 */
public final class RecordDispatch {

  private final Map<ValueType, Map<Intent, RecordHandler>> byTypeAndIntent =
      new EnumMap<>(ValueType.class);
  private final Map<ValueType, RecordHandler> byType = new EnumMap<>(ValueType.class);

  /** Registers the handler for a {@code (type, intent)} transition. */
  public RecordDispatch on(final ValueType type, final Intent intent, final RecordHandler handler) {
    byTypeAndIntent.computeIfAbsent(type, ignored -> new HashMap<>()).put(intent, handler);
    return this;
  }

  /** Registers a handler that fires for any intent of {@code type}. */
  public RecordDispatch onAnyIntent(final ValueType type, final RecordHandler handler) {
    byType.put(type, handler);
    return this;
  }

  /** Routes a record to its handler (if any), running that transition's apply/derive/evict. */
  public void dispatch(
      final SourceRecord source, final MutableProjectionState state, final Consumer<Fact> facts) {
    final Record<?> record = source.record();
    final Map<Intent, RecordHandler> intents = byTypeAndIntent.get(record.getValueType());
    RecordHandler handler = intents == null ? null : intents.get(record.getIntent());
    if (handler == null) {
      handler = byType.get(record.getValueType());
    }
    if (handler != null) {
      handler.handle(source, state, facts);
    }
  }
}
