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
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
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
  // Keyed by (valueType, intent value). The value — not the enum instance — is the stable key: a
  // command rides a borrowed ValueType, so the platform resolves its intent against that value
  // type's own enum, preserving only the numeric value (see EventAppliers).
  private final Map<ValueType, Map<Short, TypedRecordProcessor<?>>> commandProcessors =
      new EnumMap<>(ValueType.class);
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();

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
            .putIfAbsent(intent.value(), processor);
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

  /**
   * Registers a {@link StreamProcessorLifecycleAware} listener — the event-bridge counterpart of
   * the engine's {@code TypedRecordProcessors.withListener}. The engine forwards these to the
   * platform (via {@code RecordProcessorContext#addLifecycleListeners}), so each listener is
   * notified of the processor lifecycle. A background task (e.g. the rebalance assignor) implements
   * both {@link io.camunda.zeebe.stream.api.scheduling.Task} and this interface, and self-schedules
   * on its {@code onRecovered} — leader only, off the command-processing path, after the async task
   * group is up.
   */
  public RecordProcessors withListener(final StreamProcessorLifecycleAware listener) {
    lifecycleListeners.add(listener);
    return this;
  }

  Map<ValueType, Map<Short, TypedRecordProcessor<?>>> commandProcessors() {
    return commandProcessors;
  }

  List<StreamProcessorLifecycleAware> lifecycleListeners() {
    return lifecycleListeners;
  }
}
