/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.BehaviorBuilder;
import akka.actor.typed.javadsl.Behaviors;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.Command;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.GetPositions;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.Positions;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.RecordAvailable;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.state.mutable.MutableLastProcessedPositionState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.util.exception.RecoverableException;
import io.camunda.zeebe.util.exception.UnrecoverableException;

public class AkkaStreamProcessing {
  private static final MetadataFilter PROCESSING_FILTER =
      recordMetadata -> recordMetadata.getRecordType() == RecordType.COMMAND;
  AkkaStreamProcessor streamProcessor;
  ActorContext<Command> ctx;
  final LogStreamReader logStreamReader;
  private final EventFilter eventFilter =
      new MetadataEventFilter(new RecordProtocolVersionFilter().and(PROCESSING_FILTER));
  private final TransactionContext transactionContext;
  private TypedEventImpl typedCommand;
  private final TypedStreamWriter logStreamWriter;
  private TypedResponseWriter responseWriter;
  private final MutableZeebeState zeebeState;
  private final RecordValues recordValues;
  private SideEffectProducer sideEffectProducer;
  private final MutableLastProcessedPositionState lastProcessedPositionState;
  private long writtenPosition = StreamProcessor.UNSET_POSITION;
  private long lastSuccessfulProcessedRecordPosition;
  private long lastWrittenPosition;
  private final RecordProcessorMap recordProcessorMap;

  public AkkaStreamProcessing(
      final AkkaStreamProcessor streamProcessor, final ProcessingContext context) {
    this.streamProcessor = streamProcessor;
    transactionContext = context.getTransactionContext();
    logStreamReader = context.getLogStreamReader();
    logStreamWriter = context.getLogStreamWriter();
    zeebeState = context.getZeebeState();
    recordValues = context.getRecordValues();
    lastProcessedPositionState = context.getLastProcessedPositionState();
    recordProcessorMap = context.getRecordProcessorMap();
  }

  Behavior<Command> startProcessing() {
    return Behaviors.setup(
        (ctx) -> {
          this.ctx = ctx;
          return processNextEvent();
        });
  }

  private BehaviorBuilder<Command> common() {
    return streamProcessor
        .common()
        .onMessage(
            GetPositions.class,
            (msg) -> {
              msg.replyTo()
                  .tell(
                      new Positions(
                          lastWrittenPosition,
                          lastProcessedPositionState.getLastSuccessfulProcessedRecordPosition()));
              return Behaviors.same();
            });
  }

  private Behavior<Command> processNextEvent() throws Exception {
    final var hasNext = logStreamReader.hasNext();
    if (hasNext) {
      final var record = logStreamReader.next();
      if (eventFilter.applies(record)) {
        processCommand(record);
      }
      return processing();
    } else {
      return common().onMessage(RecordAvailable.class, (msg) -> processing()).build();
    }
  }

  private Behavior<Command> processing() {
    ctx.getSelf().tell(new ProcessNextEvent());

    return common().onMessage(ProcessNextEvent.class, (msg) -> processNextEvent()).build();
  }

  private Behavior<Command> retry(final LoggedEvent command) {
    return common()
        .onMessage(RetryProcessingCommand.class, (msg) -> processCommand(command))
        .build();
  }

  private Behavior<Command> processCommand(final LoggedEvent command) throws Exception {
    final var metadata = new RecordMetadata();
    command.readMetadata(metadata);

    final var currentProcessor = chooseNextProcessor(command, metadata);
    if (currentProcessor == null) {
      return processing();
    }

    // Here we need to get the current time, since we want to calculate
    // how long it took between writing to the dispatcher and processing.
    // In all other cases we should prefer to use the Prometheus Timer API.
    try {
      final var value = recordValues.readRecordValue(command, metadata.getValueType());
      typedCommand.wrap(command, metadata, value);

      processInTransaction(currentProcessor, typedCommand);

      writeRecords(command);
    } catch (final RecoverableException recoverableException) {
      // recoverable
      // TODO: Just rethrow here and let the actor restart?
      ctx.getLog().error("Processing command failed", recoverableException);
      return retry(command);
    } catch (final UnrecoverableException unrecoverableException) {
      throw unrecoverableException;
    } catch (final Exception e) {
      ctx.getLog().error("Processing command unexpectedly failed", e);
      onError(e, () -> writeRecords(command));
    }
    return processing();
  }

  private void processInTransaction(
      final TypedRecordProcessor<?> currentProcessor, final TypedEventImpl typedRecord)
      throws Exception {
    final var zeebeDbTransaction = transactionContext.getCurrentTransaction();
    zeebeDbTransaction.run(
        () -> {
          final long position = typedRecord.getPosition();
          resetOutput(position);

          // default side effect is responses; can be changed by processor
          sideEffectProducer = responseWriter;
          final boolean isNotOnBlacklist =
              !zeebeState.getBlackListState().isOnBlacklist(typedRecord);
          if (isNotOnBlacklist) {
            currentProcessor.processRecord(
                position,
                typedRecord,
                responseWriter,
                logStreamWriter,
                this::setSideEffectProducer);
          }

          lastProcessedPositionState.markAsProcessed(position);
        });
  }

  private void writeRecords(final LoggedEvent command) {
    final long position = logStreamWriter.flush();
    if (position > 0) {
      writtenPosition = position;
    }
    // TODO: Handle failed flush

    try {
      updateState(command);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void updateState(final LoggedEvent command) throws Exception {
    final var zeebeDbTransaction = transactionContext.getCurrentTransaction();
    zeebeDbTransaction.commit();
    lastSuccessfulProcessedRecordPosition = command.getPosition();
    lastWrittenPosition = writtenPosition;
    executeSideEffects();
    // TODO: Retry
  }

  private void executeSideEffects() {
    // TODO: Retry flush if needed
    sideEffectProducer.flush();
  }

  public void setSideEffectProducer(final SideEffectProducer sideEffectProducer) {
    this.sideEffectProducer = sideEffectProducer;
  }

  private void onError(final Throwable processingException, final Runnable nextStep)
      throws Exception {
    final var zeebeDbTransaction = transactionContext.getCurrentTransaction();

    zeebeDbTransaction.rollback();
    errorHandlingInTransaction(processingException);
    nextStep.run();
  }

  private void errorHandlingInTransaction(final Throwable processingException) throws Exception {
    final var zeebeDbTransaction = transactionContext.getCurrentTransaction();
    zeebeDbTransaction.run(
        () -> {
          final long position = typedCommand.getPosition();
          resetOutput(position);

          writeRejectionOnCommand(processingException);
          final var errorRecord = new ErrorRecord();
          errorRecord.initErrorRecord(processingException, position);

          zeebeState
              .getBlackListState()
              .tryToBlacklist(typedCommand, errorRecord::setProcessInstanceKey);

          logStreamWriter.appendFollowUpEvent(
              typedCommand.getKey(), ErrorIntent.CREATED, errorRecord);
        });
  }

  private void resetOutput(final long sourceRecordPosition) {
    responseWriter.reset();
    logStreamWriter.reset();
    logStreamWriter.configureSourceContext(sourceRecordPosition);
  }

  private void writeRejectionOnCommand(final Throwable exception) {
    final String errorMessage =
        String.format("PROCESSING_ERROR_MESSAGE", typedCommand, exception.getMessage());
    //    LOG.error(errorMessage, exception);

    logStreamWriter.appendRejection(typedCommand, RejectionType.PROCESSING_ERROR, errorMessage);
    responseWriter.writeRejectionOnCommand(
        typedCommand, RejectionType.PROCESSING_ERROR, errorMessage);
  }

  private TypedRecordProcessor<?> chooseNextProcessor(
      final LoggedEvent command, final RecordMetadata metadata) {
    TypedRecordProcessor<?> typedRecordProcessor = null;

    try {
      typedRecordProcessor =
          recordProcessorMap.get(
              metadata.getRecordType(), metadata.getValueType(), metadata.getIntent().value());
    } catch (final Exception e) {
      ctx.getLog().error("ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT {} {}", command, metadata, e);
    }

    return typedRecordProcessor;
  }

  record RetryProcessingCommand() implements Command {}

  record ProcessNextEvent() implements Command {}
}
