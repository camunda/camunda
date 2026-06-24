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
 * Registry of {@link TypedEventApplier}s keyed by event-intent <em>value</em> — the event-bridge
 * counterpart of the Zeebe engine's {@code EventAppliers}. A {@link RecordProcessingEngine}
 * registers one applier per event intent; adding a new event type is then a single {@link
 * #register} call. {@link #applyState} dispatches a committed event to the matching applier.
 *
 * <p>Keying by {@code intent.value()} rather than the enum instance is deliberate: a coordinator
 * record rides a borrowed {@link io.camunda.zeebe.protocol.record.ValueType}, so on replay the
 * platform resolves its intent against that value type's own enum (e.g. {@code CLOCK} → {@code
 * ClockIntent}), not the {@code CoordinatorIntent} the leader wrote. The numeric value is preserved
 * across that round trip, so it — not the enum identity — is the stable dispatch key.
 */
public final class EventAppliers implements EventApplier {

  @SuppressWarnings("rawtypes")
  private final Map<Short, TypedEventApplier> appliers = new HashMap<>();

  /**
   * Registers the applier for an event intent.
   *
   * @return this registry, for fluent registration
   */
  public <I extends Intent, V extends RecordValue> EventAppliers register(
      final I intent, final TypedEventApplier<I, V> applier) {
    Objects.requireNonNull(intent, "intent must not be null");
    Objects.requireNonNull(applier, "applier must not be null");
    if (appliers.putIfAbsent(intent.value(), applier) != null) {
      throw new IllegalArgumentException(
          "Applier for intent '%s' is already registered".formatted(intent));
    }
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void applyState(final long key, final Intent intent, final RecordValue value) {
    final var applier = appliers.get(intent.value());
    if (applier == null) {
      throw new NoSuchEventApplier(intent);
    }
    applier.applyState(key, value);
  }
}
