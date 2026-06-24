/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.api.EmptyProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single {@link RecordProcessor} a {@link ReplicatedStream} runs — the event-bridge counterpart
 * of the Zeebe engine's {@code Engine}. It owns a registry of command {@link TypedRecordProcessor}s
 * (keyed by {@code (ValueType, Intent)}) and a single {@link EventAppliers} registry (keyed by
 * event intent). As in the engine, the {@link Writers} are built once against a {@link
 * ProcessingResultBuilderMutex} and handed to the command processors at <em>construction</em> (via
 * the {@link RecordProcessorsFactory}); {@link #process} only opens a {@link
 * ProcessingResultBuilderScope} so those writers target the current result builder.
 *
 * <ul>
 *   <li>{@link #process} (leader) looks up the command processor for the record's value type and
 *       intent and runs it; the processor appends follow-up events via its state writer, which
 *       applies them through the event appliers in the same step.
 *   <li>{@link #replay} (follower/observer) dispatches the committed event to its applier by
 *       intent.
 * </ul>
 *
 * <p>Both commands and their resulting events share a value type, so {@link #accepts} (derived from
 * the command registrations) covers replay as well.
 */
public final class RecordProcessingEngine implements RecordProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(RecordProcessingEngine.class);

  private final ProcessingResultBuilderMutex resultBuilderMutex =
      new ProcessingResultBuilderMutex();
  private final EventAppliers eventAppliers = new EventAppliers();
  private final Map<ValueType, Map<Short, TypedRecordProcessor<?>>> commandProcessors;
  private final Set<ValueType> acceptedValueTypes;
  private final List<StreamProcessorLifecycleAware> lifecycleListeners;

  public RecordProcessingEngine(final RecordProcessorsFactory recordProcessorsFactory) {
    final var writers = new Writers(resultBuilderMutex, eventAppliers);
    final var processors = new RecordProcessors(writers, eventAppliers);
    recordProcessorsFactory.createProcessors(processors);
    commandProcessors = processors.commandProcessors();
    acceptedValueTypes = Set.copyOf(commandProcessors.keySet());
    lifecycleListeners = processors.lifecycleListeners();
  }

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    // Forward the registered lifecycle listeners to the platform — mirroring how the engine hands
    // its TypedRecordProcessors' listeners to the StreamProcessor. A background task (e.g. the
    // rebalance assignor) is such a listener and self-schedules in its onRecovered, which fires
    // only
    // after recovery and only on the leader (followers stay in replay).
    if (!lifecycleListeners.isEmpty()) {
      recordProcessorContext.addLifecycleListeners(lifecycleListeners);
    }
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
    try (final var scope = new ProcessingResultBuilderScope(processingResultBuilder)) {
      final var byIntent = commandProcessors.get(record.getValueType());
      final var processor = byIntent == null ? null : byIntent.get(record.getIntent().value());
      if (processor != null) {
        invoke(processor, record);
      }
    }
    return processingResultBuilder.build();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void invoke(final TypedRecordProcessor processor, final TypedRecord record) {
    processor.processRecord(record);
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    // A command processor threw. Log it (otherwise the failure is invisible and the waiting caller
    // just times out), then yield an empty result so the stream skips the record rather than
    // looping on it.
    LOG.error(
        "Error processing {} command (intent {}); skipping the record",
        record.getValueType(),
        record.getIntent(),
        processingException);
    return EmptyProcessingResult.INSTANCE;
  }

  /**
   * Holds the result builder for the duration of a single {@link #process} call so the writers
   * built against it (in the constructor) resolve to the right builder. Mirrors the engine's mutex.
   */
  private static final class ProcessingResultBuilderMutex
      implements Supplier<ProcessingResultBuilder> {

    private ProcessingResultBuilder resultBuilder;

    private void setResultBuilder(final ProcessingResultBuilder resultBuilder) {
      this.resultBuilder = Objects.requireNonNull(resultBuilder);
    }

    private void unsetResultBuilder() {
      resultBuilder = null;
    }

    @Override
    public ProcessingResultBuilder get() {
      if (resultBuilder == null) {
        throw new IllegalStateException("Attempt to retrieve resultBuilder out of scope.");
      }
      return resultBuilder;
    }
  }

  /** Scopes the result builder to one {@link #process} call (set on enter, unset on close). */
  private final class ProcessingResultBuilderScope implements AutoCloseable {

    private ProcessingResultBuilderScope(final ProcessingResultBuilder processingResultBuilder) {
      resultBuilderMutex.setResultBuilder(processingResultBuilder);
    }

    @Override
    public void close() {
      resultBuilderMutex.unsetResultBuilder();
    }
  }
}
