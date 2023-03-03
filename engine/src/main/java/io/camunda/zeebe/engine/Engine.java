/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine;

import io.camunda.zeebe.engine.processing.streamprocessor.RecordProcessorMap;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContextImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.ScheduledTaskDbState;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.processing.DbBlackListState;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;

public class Engine implements RecordProcessor {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private static final String ERROR_MESSAGE_PROCESSOR_NOT_FOUND =
      "Expected to find processor for record '{}', but caught an exception. Skip this record.";
  private static final String ERROR_MESSAGE_PROCESSING_EXCEPTION_OCCURRED =
      "Expected to process record '%s' without errors, but exception occurred with message '%s'.";

  private static final EnumSet<ValueType> SUPPORTED_VALUETYPES =
      EnumSet.range(ValueType.JOB, ValueType.RESOURCE_DELETION);

  private EventApplier eventApplier;
  private RecordProcessorMap recordProcessorMap;
  private ProcessingDbState processingState;

  private final ErrorRecord errorRecord = new ErrorRecord();

  private final ProcessingResultBuilderMutex resultBuilderMutex =
      new ProcessingResultBuilderMutex();

  private Writers writers;
  private TypedRecordProcessorFactory typedRecordProcessorFactory;

  public Engine() {}

  public Engine(final TypedRecordProcessorFactory typedRecordProcessorFactory) {
    this.typedRecordProcessorFactory = typedRecordProcessorFactory;
  }

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    final var zeebeDb = recordProcessorContext.getZeebeDb();
    processingState =
        new ProcessingDbState(
            recordProcessorContext.getPartitionId(),
            zeebeDb,
            recordProcessorContext.getTransactionContext(),
            recordProcessorContext.getKeyGenerator(),
            recordProcessorContext.jobStreamer());
    final var scheduledTaskDbState = new ScheduledTaskDbState(zeebeDb, zeebeDb.createContext());

    eventApplier = new EventAppliers(processingState);

    writers = new Writers(resultBuilderMutex, eventApplier);

    final var typedProcessorContext =
        new TypedRecordProcessorContextImpl(
            recordProcessorContext.getPartitionId(),
            recordProcessorContext.getScheduleService(),
            processingState,
            scheduledTaskDbState,
            writers,
            recordProcessorContext.getPartitionCommandSender());

    final TypedRecordProcessors typedRecordProcessors =
        typedRecordProcessorFactory.createProcessors(typedProcessorContext);

    recordProcessorContext.addLifecycleListeners(typedRecordProcessors.getLifecycleListeners());
    recordProcessorMap = typedRecordProcessors.getRecordProcessorMap();
  }

  @Override
  public boolean accepts(final ValueType valueType) {
    return SUPPORTED_VALUETYPES.contains(valueType);
  }

  @Override
  public void replay(final TypedRecord event) {
    eventApplier.applyState(event.getKey(), event.getIntent(), event.getValue());
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder processingResultBuilder) {

    try (final var scope = new ProcessingResultBuilderScope(processingResultBuilder)) {
      TypedRecordProcessor<?> currentProcessor = null;

      final var typedCommand = (TypedRecord<?>) record;
      try {
        currentProcessor =
            recordProcessorMap.get(
                typedCommand.getRecordType(),
                typedCommand.getValueType(),
                typedCommand.getIntent().value());
      } catch (final Exception e) {
        LOG.error(ERROR_MESSAGE_PROCESSOR_NOT_FOUND, typedCommand, e);
      }

      if (currentProcessor == null) {
        return processingResultBuilder.build();
      }

      final boolean isNotOnBlacklist =
          !processingState.getBlackListState().isOnBlacklist(typedCommand);
      if (isNotOnBlacklist) {
        currentProcessor.processRecord(record);
      }
    }
    return processingResultBuilder.build();
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    try (final var scope = new ProcessingResultBuilderScope(processingResultBuilder)) {

      final var typedCommand = (TypedRecord<?>) record;
      TypedRecordProcessor<?> processor = null;
      try {
        processor =
            recordProcessorMap.get(
                typedCommand.getRecordType(),
                typedCommand.getValueType(),
                typedCommand.getIntent().value());
      } catch (final Exception e) {
        LOG.error(ERROR_MESSAGE_PROCESSOR_NOT_FOUND, typedCommand, e);
      }

      final var error =
          processor == null
              ? ProcessingError.UNEXPECTED_ERROR
              : processor.tryHandleError(record, processingException);

      if (error == ProcessingError.UNEXPECTED_ERROR) {
        handleUnexpectedError(processingException, record);
      }
    }
    return processingResultBuilder.build();
  }

  private void handleUnexpectedError(
      final Throwable processingException, final TypedRecord record) {
    final String errorMessage =
        String.format(
            ERROR_MESSAGE_PROCESSING_EXCEPTION_OCCURRED, record, processingException.getMessage());
    LOG.error(errorMessage, processingException);

    if (processingException instanceof ExceededBatchRecordSizeException) {
      // Rejection reason is left empty here. This is because we need to make sure we can write the
      // rejection. The record itself does not exceed the batch record size, but adding a reason
      // could cause it to cross the limit. When this happens the engine would reach an
      // exception-loop.
      writers.rejection().appendRejection(record, RejectionType.EXCEEDED_BATCH_RECORD_SIZE, "");
      writers
          .response()
          .writeRejectionOnCommand(record, RejectionType.EXCEEDED_BATCH_RECORD_SIZE, "");
    } else {
      writers.rejection().appendRejection(record, RejectionType.PROCESSING_ERROR, errorMessage);
      writers
          .response()
          .writeRejectionOnCommand(record, RejectionType.PROCESSING_ERROR, errorMessage);
    }
    errorRecord.initErrorRecord(processingException, record.getPosition());

    if (DbBlackListState.shouldBeBlacklisted(record.getIntent())) {
      if (record.getValue() instanceof ProcessInstanceRelated) {
        final long processInstanceKey =
            ((ProcessInstanceRelated) record.getValue()).getProcessInstanceKey();
        errorRecord.setProcessInstanceKey(processInstanceKey);
      }

      writers.state().appendFollowUpEvent(record.getKey(), ErrorIntent.CREATED, errorRecord);
    }
  }

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
