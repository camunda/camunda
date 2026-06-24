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
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Base command processor following the Zeebe workflow-engine split of command handling from state
 * mutation. Subclasses register one {@link TypedEventApplier} per event intent (via {@link
 * #appliers()}) and implement {@link #processCommand}, where they turn a command into follow-up
 * events using the {@link #stateWriter()}. State is mutated only by the registered appliers, so the
 * leader (on {@link #process}, when the writer applies the event) and followers (on {@link
 * #replay}) run identical logic and converge.
 *
 * <p>Dispatch is by {@code RecordType}: the platform calls {@link #process} for COMMAND records and
 * {@link #replay} for EVENT records. Each processor owns a single {@link ValueType}, declared via
 * {@link #accepts}.
 */
public abstract class StreamRecordProcessor implements RecordProcessor {

  private final ValueType valueType;
  private final EventAppliers eventAppliers = new EventAppliers();
  private final StateWriter stateWriter;
  private ProcessingResultBuilder currentResultBuilder;

  protected StreamRecordProcessor(final ValueType valueType) {
    this.valueType = valueType;
    stateWriter = new StateWriter(() -> currentResultBuilder, eventAppliers);
  }

  /** The applier registry; subclasses register their per-intent appliers on it in their ctor. */
  protected final EventAppliers appliers() {
    return eventAppliers;
  }

  /**
   * Writes follow-up events that are applied in the same step; valid only during {@link #process}.
   */
  protected final StateWriter stateWriter() {
    return stateWriter;
  }

  /**
   * Handles a command record, typically by appending follow-up events via {@link #stateWriter()}.
   */
  protected abstract void processCommand(TypedRecord command);

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    // State is injected through the concrete subclass; nothing to initialize here.
  }

  @Override
  public boolean accepts(final ValueType recordValueType) {
    return recordValueType == valueType;
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
      processCommand(record);
      return processingResultBuilder.build();
    } finally {
      currentResultBuilder = null;
    }
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    return EmptyProcessingResult.INSTANCE;
  }
}
