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
import io.camunda.zeebe.stream.api.EmptyProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The single {@link RecordProcessor} a {@link ReplicatedStream} runs — the event-bridge counterpart
 * of the Zeebe engine's {@code Engine}. It owns a registry of command {@link TypedRecordProcessor}s
 * (keyed by {@code (ValueType, Intent)}) and a single {@link EventAppliers} registry (keyed by
 * event intent), sharing one {@link StateWriter}.
 *
 * <ul>
 *   <li>{@link #process} (leader) looks up the command processor for the record's value type and
 *       intent and runs it; the processor appends follow-up events via the state writer, which
 *       applies them through the event appliers in the same step.
 *   <li>{@link #replay} (follower/observer) dispatches the committed event to its applier by
 *       intent.
 * </ul>
 *
 * <p>Build one per stream via {@link #builder()}: register a processor per command intent and an
 * applier per event intent. Both commands and their resulting events share a value type, so {@link
 * #accepts} covers replay as well.
 */
public final class RecordProcessingEngine implements RecordProcessor {

  private final Map<ValueType, Map<Intent, TypedRecordProcessor<?>>> commandProcessors;
  private final EventAppliers eventAppliers;
  private final Set<ValueType> acceptedValueTypes;
  private final StateWriter stateWriter;

  private ProcessingResultBuilder currentResultBuilder;

  private RecordProcessingEngine(
      final Map<ValueType, Map<Intent, TypedRecordProcessor<?>>> commandProcessors,
      final EventAppliers eventAppliers) {
    this.commandProcessors = commandProcessors;
    this.eventAppliers = eventAppliers;
    acceptedValueTypes = Set.copyOf(commandProcessors.keySet());
    stateWriter = new StateWriter(() -> currentResultBuilder, eventAppliers);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    // State is captured by the registered processors/appliers; nothing to initialize here.
  }

  @Override
  public boolean accepts(final ValueType valueType) {
    return acceptedValueTypes.contains(valueType);
  }

  @Override
  public void replay(final TypedRecord record) {
    eventAppliers.applyState(record.getKey(), record.getIntent(), record.getValue());
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder processingResultBuilder) {
    currentResultBuilder = processingResultBuilder;
    try {
      final var byIntent = commandProcessors.get(record.getValueType());
      final var processor = byIntent == null ? null : byIntent.get(record.getIntent());
      if (processor != null) {
        invoke(processor, record);
      }
      return processingResultBuilder.build();
    } finally {
      currentResultBuilder = null;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void invoke(final TypedRecordProcessor processor, final TypedRecord record) {
    processor.processRecord(record, stateWriter);
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    return EmptyProcessingResult.INSTANCE;
  }

  /** Registers the command processors and event appliers for one stream's engine. */
  public static final class Builder {

    private final Map<ValueType, Map<Intent, TypedRecordProcessor<?>>> commandProcessors =
        new EnumMap<>(ValueType.class);
    private final EventAppliers eventAppliers = new EventAppliers();

    private Builder() {}

    /** Registers the processor that handles the given command intent. */
    public <T extends UnifiedRecordValue> Builder onCommand(
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
    public <I extends Intent, V extends RecordValue> Builder withEventApplier(
        final I intent, final TypedEventApplier<I, V> applier) {
      eventAppliers.register(intent, applier);
      return this;
    }

    public RecordProcessingEngine build() {
      return new RecordProcessingEngine(commandProcessors, eventAppliers);
    }
  }
}
