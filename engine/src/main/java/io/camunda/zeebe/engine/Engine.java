/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine;

import io.camunda.zeebe.engine.api.EmptyProcessingResult;
import io.camunda.zeebe.engine.api.ErrorHandlingContext;
import io.camunda.zeebe.engine.api.ProcessingContext;
import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.RecordProcessor;
import io.camunda.zeebe.engine.api.RecordProcessorContext;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.RecordProcessorMap;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContextImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import org.slf4j.Logger;

public class Engine implements RecordProcessor {

  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;
  private static final String ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT =
      "Expected to find processor for record '{}', but caught an exception. Skip this record.";
  private static final String PROCESSING_ERROR_MESSAGE =
      "Expected to process record '%s' without errors, but exception occurred with message '%s'.";
  private EventApplier eventApplier;
  private RecordProcessorMap recordProcessorMap;
  private MutableZeebeState zeebeState;
  private TypedStreamWriter streamWriter;
  private TypedResponseWriter responseWriter;

  private final ErrorRecord errorRecord = new ErrorRecord();

  private Writers writers;

  public Engine() {}

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    streamWriter = recordProcessorContext.getStreamWriterProxy();
    responseWriter = recordProcessorContext.getTypedResponseWriter();

    final var typedProcessorContext =
        new TypedRecordProcessorContextImpl(
            recordProcessorContext.getPartitionId(),
            recordProcessorContext.getScheduleService(),
            recordProcessorContext.getZeebeDb(),
            recordProcessorContext.getTransactionContext(),
            streamWriter,
            recordProcessorContext.getEventApplierFactory(),
            responseWriter);

    final TypedRecordProcessors typedRecordProcessors =
        recordProcessorContext
            .getTypedRecordProcessorFactory()
            .createProcessors(typedProcessorContext);

    recordProcessorContext.setStreamProcessorListener(
        typedProcessorContext.getStreamProcessorListener());

    recordProcessorContext.setLifecycleListeners(typedRecordProcessors.getLifecycleListeners());
    recordProcessorMap = typedRecordProcessors.getRecordProcessorMap();

    writers = typedProcessorContext.getWriters();
    recordProcessorContext.setWriters(writers);
    zeebeState = typedProcessorContext.getZeebeState();
    eventApplier = recordProcessorContext.getEventApplierFactory().apply(zeebeState);
  }

  @Override
  public void replay(final TypedRecord event) {
    eventApplier.applyState(event.getKey(), event.getIntent(), event.getValue());
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingContext processingContext) {
    TypedRecordProcessor<?> currentProcessor = null;

    final var typedCommand = (TypedRecord<?>) record;
    try {
      currentProcessor =
          recordProcessorMap.get(
              typedCommand.getRecordType(),
              typedCommand.getValueType(),
              typedCommand.getIntent().value());
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT, typedCommand, e);
    }

    if (currentProcessor == null) {
      return EmptyProcessingResult.INSTANCE;
    }

    final var processingResultBuilder = processingContext.getProcessingResultBuilder();

    final boolean isNotOnBlacklist = !zeebeState.getBlackListState().isOnBlacklist(typedCommand);
    if (isNotOnBlacklist) {
      final long position = typedCommand.getPosition();
      currentProcessor.processRecord(
          position,
          record,
          responseWriter,
          streamWriter,
          (sep) -> {
            processingResultBuilder.resetPostCommitTasks();
            processingResultBuilder.appendPostCommitTask(sep::flush);
          });
    }

    return processingResultBuilder.build();
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ErrorHandlingContext errorHandlingContext) {

    final String errorMessage =
        String.format(PROCESSING_ERROR_MESSAGE, record, processingException.getMessage());
    LOG.error(errorMessage, processingException);

    final var processingResultBuilder = errorHandlingContext.getProcessingResultBuilder();

    writers.rejection().appendRejection(record, RejectionType.PROCESSING_ERROR, errorMessage);
    writers
        .response()
        .writeRejectionOnCommand(record, RejectionType.PROCESSING_ERROR, errorMessage);
    errorRecord.initErrorRecord(processingException, record.getPosition());

    writers.state().appendFollowUpEvent(record.getKey(), ErrorIntent.CREATED, errorRecord);
    return processingResultBuilder.build();
  }
}
