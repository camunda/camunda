/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The registration context handed to a {@link RecordProcessorsFactory} when a {@link
 * RecordProcessingEngine} is built — the event-bridge counterpart of the engine's {@code
 * TypedRecordProcessors} plus {@code Writers}. It exposes the {@link #writers()} a command
 * processor needs at construction and collects the command processors and event appliers registered
 * for the stream.
 */
public final class RecordProcessors {

  private final Writers writers;
  private final EventAppliers eventAppliers;
  private final Map<ValueType, Map<Intent, TypedRecordProcessor<?>>> commandProcessors =
      new EnumMap<>(ValueType.class);

  RecordProcessors(final Writers writers, final EventAppliers eventAppliers) {
    this.writers = writers;
    this.eventAppliers = eventAppliers;
  }

  /** The writers (state / command / response) to hand to command processors at construction. */
  public Writers writers() {
    return writers;
  }

  /** Registers the processor that handles the given command intent. */
  public <T extends UnifiedRecordValue> RecordProcessors onCommand(
      final ValueType valueType, final Intent intent, final TypedRecordProcessor<T> processor) {
    final var previous =
        commandProcessors
            .computeIfAbsent(valueType, unused -> new HashMap<>())
            .putIfAbsent(intent, processor);
    if (previous != null) {
      throw new IllegalArgumentException(
          "Command processor for %s/%s is already registered".formatted(valueType, intent));
    }
    return this;
  }

  /** Registers the applier that mutates state for the given event intent. */
  public <I extends Intent, V extends RecordValue> RecordProcessors withEventApplier(
      final I intent, final TypedEventApplier<I, V> applier) {
    eventAppliers.register(intent, applier);
    return this;
  }

  Map<ValueType, Map<Intent, TypedRecordProcessor<?>>> commandProcessors() {
    return commandProcessors;
  }
}
