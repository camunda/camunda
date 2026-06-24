/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of {@link TypedEventApplier}s keyed by event intent — the event-bridge counterpart of
 * the Zeebe engine's {@code EventAppliers}. A {@link RecordProcessingEngine} registers one applier
 * per event intent; adding a new event type is then a single {@link #register} call. {@link
 * #applyState} dispatches a committed event to the matching applier.
 */
public final class EventAppliers implements EventApplier {

  @SuppressWarnings("rawtypes")
  private final Map<Intent, TypedEventApplier> appliers = new HashMap<>();

  /**
   * Registers the applier for an event intent.
   *
   * @return this registry, for fluent registration
   */
  public <I extends Intent, V extends RecordValue> EventAppliers register(
      final I intent, final TypedEventApplier<I, V> applier) {
    Objects.requireNonNull(intent, "intent must not be null");
    Objects.requireNonNull(applier, "applier must not be null");
    if (appliers.putIfAbsent(intent, applier) != null) {
      throw new IllegalArgumentException(
          "Applier for intent '%s' is already registered".formatted(intent));
    }
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void applyState(final long key, final Intent intent, final RecordValue value) {
    final var applier = appliers.get(intent);
    if (applier == null) {
      throw new NoSuchEventApplier(intent);
    }
    applier.applyState(key, value);
  }
}
