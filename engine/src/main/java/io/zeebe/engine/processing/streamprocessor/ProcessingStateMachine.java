/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.db.TransactionContext;
import io.zeebe.db.ZeebeDbTransaction;
import io.zeebe.engine.metrics.StreamProcessorMetrics;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.mutable.MutableLastProcessedPositionState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ErrorIntent;
import io.zeebe.util.exception.RecoverableException;
import io.zeebe.util.retry.AbortableRetryStrategy;
import io.zeebe.util.retry.RecoverableRetryStrategy;
import io.zeebe.util.retry.RetryStrategy;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Represents the processing state machine, which is executed on normal processing.
 *
 * <pre>
 *
 * +-----------------+             +--------------------+
 * |                 |             |                    |      exception
 * | readNextEvent() |------------>|   processEvent()   |------------------+
 * |                 |             |                    |                  v
 * +-----------------+             +--------------------+            +---------------+
 *           ^                             |                         |               |------+
 *           |                             |         +-------------->|   onError()   |      | exception
 *           |                             |         |  exception    |               |<-----+
 *           |                     +-------v-------------+           +---------------+
 *           |                     |                     |                 |
 *           |                     |    writeEvent()     |                 |
 *           |                     |                     |<----------------+
 * +----------------------+        +---------------------+
 * |                      |                 |
 * | executeSideEffects() |                 v
 * |                      |       +----------------------+
 * +----------------------+       |                      |
 *           ^                    |     updateState()    |
 *           +--------------------|                      |
 *                                +----------------------+
 *                                       ^      |
 *                                       |      | exception
 *                                       |      |
 *                                    +---------v----+
 *                                    |              |
 *                                    |   onError()  |
 *                                    |              |
 *                                    +--------------+
 *                                       ^     |
 *                                       |     |  exception
 *                                       +-----+
 *
 * </pre>
 */
public final class ProcessingStateMachine {

  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;
  private static final String ERROR_MESSAGE_WRITE_EVENT_ABORTED =
      "Expected to write one or more follow up events for event '{} {}' without errors, but exception was thrown.";
  private static final String ERROR_MESSAGE_ROLLBACK_ABORTED =
      "Expected to roll back the current transaction for event '{} {}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_EXECUTE_SIDE_EFFECT_ABORTED =
      "Expected to execute side effects for event '{} {}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_UPDATE_STATE_FAILED =
      "Expected to successfully update state for event '{} {}', but caught an exception. Retry.";
  private static final String ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT =
      "Expected to find event processor for event '{} {}', but caught an exception. Skip this event.";
  private static final String ERROR_MESSAGE_PROCESSING_FAILED_SKIP_EVENT =
      "Expected to successfully process event '{} {}' with processor, but caught an exception. Skip this event.";
  private static final String ERROR_MESSAGE_PROCESSING_FAILED_RETRY_PROCESSING =
      "Expected to process event '{} {}' successfully on stream processor, but caught recoverable exception. Retry processing.";
  private static final String PROCESSING_ERROR_MESSAGE =
      "Expected to process event '%s' without errors, but exception occurred with message '%s'.";
  private static final String NOTIFY_PROCESSED_LISTENER_ERROR_MESSAGE =
      "Expected to invoke processed listener for record {} successfully, but exception was thrown.";
  private static final String NOTIFY_SKIPPED_LISTENER_ERROR_MESSAGE =
      "Expected to invoke skipped listener for record '{} {}' successfully, but exception was thrown.";
  private static final String LOG_ERROR_EVENT_COMMITTED =
      "Error event was committed, we continue with processing.";
  private static final String LOG_ERROR_EVENT_WRITTEN =
      "Error record was written at {}, we will continue with processing if event was committed. Current commit position is {}.";

  private static final Duration PROCESSING_RETRY_DELAY = Duration.ofMillis(250);

  private static final MetadataFilter PROCESSING_FILTER =
      recordMetadata ->
          recordMetadata.getRecordType() == RecordType.COMMAND
              || !MigratedStreamProcessors.isMigrated(recordMetadata.getValueType());

  private final EventFilter eventFilter =
      new MetadataEventFilter(new RecordProtocolVersionFilter().and(PROCESSING_FILTER));

  private final MutableZeebeState zeebeState;
  private final MutableLastProcessedPositionState lastProcessedPositionState;
  private final RecordMetadata metadata = new RecordMetadata();
  private final TypedResponseWriter responseWriter;
  private final ActorControl actor;
  private final LogStream logStream;
  private final LogStreamReader logStreamReader;
  private final TypedStreamWriter logStreamWriter;
  private final TransactionContext transactionContext;
  private final RetryStrategy writeRetryStrategy;
  private final RetryStrategy sideEffectsRetryStrategy;
  private final RetryStrategy updateStateRetryStrategy;
  private final BooleanSupplier shouldProcessNext;
  private final BooleanSupplier abortCondition;
  private final ErrorRecord errorRecord = new ErrorRecord();
  private final RecordValues recordValues;
  private final RecordProcessorMap recordProcessorMap;
  private final TypedEventImpl typedEvent;
  private final StreamProcessorMetrics metrics;
  private final Consumer<TypedRecord> onProcessedListener;
  private final Consumer<LoggedEvent> onSkippedListener;

  // current iteration
  private SideEffectProducer sideEffectProducer;
  private LoggedEvent currentEvent;
  private TypedRecordProcessor<?> currentProcessor;
  private ZeebeDbTransaction zeebeDbTransaction;
  private long writtenEventPosition = StreamProcessor.UNSET_POSITION;
  private long lastSuccessfulProcessedEventPosition = StreamProcessor.UNSET_POSITION;
  private long lastWrittenEventPosition = StreamProcessor.UNSET_POSITION;
  private boolean onErrorHandling;
  private long errorRecordPosition = StreamProcessor.UNSET_POSITION;
  private volatile boolean onErrorHandlingLoop;
  private int onErrorRetries;
  // Used for processing duration metrics
  private long processingStartTime;

  public ProcessingStateMachine(
      final ProcessingContext context, final BooleanSupplier shouldProcessNext) {

    actor = context.getActor();
    recordProcessorMap = context.getRecordProcessorMap();
    recordValues = context.getRecordValues();
    logStreamReader = context.getLogStreamReader();
    logStreamWriter = context.getLogStreamWriter();
    logStream = context.getLogStream();
    zeebeState = context.getZeebeState();
    transactionContext = context.getTransactionContext();
    abortCondition = context.getAbortCondition();
    lastProcessedPositionState = context.getLastProcessedPositionState();

    writeRetryStrategy = new AbortableRetryStrategy(actor);
    sideEffectsRetryStrategy = new AbortableRetryStrategy(actor);
    updateStateRetryStrategy = new RecoverableRetryStrategy(actor);
    this.shouldProcessNext = shouldProcessNext;

    final int partitionId = logStream.getPartitionId();
    typedEvent = new TypedEventImpl(partitionId);
    responseWriter = context.getWriters().response();

    metrics = new StreamProcessorMetrics(partitionId);
    onProcessedListener = context.getOnProcessedListener();
    onSkippedListener = context.getOnSkippedListener();
  }

  private void skipRecord() {
    notifySkippedListener(currentEvent);
    actor.submit(this::readNextEvent);
    metrics.eventSkipped();
  }

  void readNextEvent() {
    if (onErrorRetries > 0) {
      onErrorHandlingLoop = false;
      onErrorRetries = 0;
    }
    if (onErrorHandling) {
      logStream
          .getCommitPositionAsync()
          .onComplete(
              (commitPosition, error) -> {
                if (error == null) {
                  if (commitPosition >= errorRecordPosition) {
                    LOG.info(LOG_ERROR_EVENT_COMMITTED);
                    onErrorHandling = false;

                    tryToReadNextEvent();
                  }
                } else {
                  LOG.error("Error on retrieving commit position", error);
                }
              });
    } else {
      tryToReadNextEvent();
    }
  }

  private void tryToReadNextEvent() {
    if (shouldProcessNext.getAsBoolean() && logStreamReader.hasNext() && currentProcessor == null) {
      currentEvent = logStreamReader.next();

      if (eventFilter.applies(currentEvent)) {
        processEvent(currentEvent);
      } else {
        skipRecord();
      }
    }
  }

  private void processEvent(final LoggedEvent event) {
    metadata.reset();
    event.readMetadata(metadata);

    currentProcessor = chooseNextProcessor(event);
    if (currentProcessor == null) {
      skipRecord();
      return;
    }

    processingStartTime = ActorClock.currentTimeMillis();

    try {
      final UnifiedRecordValue value = recordValues.readRecordValue(event, metadata.getValueType());
      typedEvent.wrap(event, metadata, value);

      // process only commands - skip events and rejections
      if (MigratedStreamProcessors.isMigrated(typedEvent)
          && typedEvent.getRecordType() != RecordType.COMMAND) {

        currentProcessor = null;
        skipRecord();
        return;
      }

      metrics.processingLatency(
          metadata.getRecordType(), event.getTimestamp(), processingStartTime);

      processInTransaction(typedEvent);

      metrics.commandsProcessed();

      writeEvent();
    } catch (final RecoverableException recoverableException) {
      // recoverable
      LOG.error(
          ERROR_MESSAGE_PROCESSING_FAILED_RETRY_PROCESSING, event, metadata, recoverableException);
      actor.runDelayed(PROCESSING_RETRY_DELAY, () -> processEvent(currentEvent));
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_PROCESSING_FAILED_SKIP_EVENT, event, metadata, e);
      onError(e, this::writeEvent);
    }
  }

  private TypedRecordProcessor<?> chooseNextProcessor(final LoggedEvent event) {
    TypedRecordProcessor<?> typedRecordProcessor = null;

    try {
      typedRecordProcessor =
          recordProcessorMap.get(
              metadata.getRecordType(), metadata.getValueType(), metadata.getIntent().value());
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT, event, metadata, e);
    }

    return typedRecordProcessor;
  }

  private void processInTransaction(final TypedEventImpl typedRecord) throws Exception {
    zeebeDbTransaction = transactionContext.getCurrentTransaction();
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

  private void resetOutput(final long sourceRecordPosition) {
    responseWriter.reset();
    logStreamWriter.reset();
    logStreamWriter.configureSourceContext(sourceRecordPosition);
  }

  public void setSideEffectProducer(final SideEffectProducer sideEffectProducer) {
    this.sideEffectProducer = sideEffectProducer;
  }

  private void onError(final Throwable processingException, final Runnable nextStep) {
    onErrorRetries++;
    if (onErrorRetries > 1) {
      onErrorHandlingLoop = true;
    }
    final ActorFuture<Boolean> retryFuture =
        updateStateRetryStrategy.runWithRetry(
            () -> {
              zeebeDbTransaction.rollback();
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(ERROR_MESSAGE_ROLLBACK_ABORTED, currentEvent, metadata, throwable);
          }
          try {
            errorHandlingInTransaction(processingException);

            onErrorHandling = true;
            nextStep.run();
          } catch (final Exception ex) {
            onError(ex, nextStep);
          }
        });
  }

  private void errorHandlingInTransaction(final Throwable processingException) throws Exception {
    zeebeDbTransaction = transactionContext.getCurrentTransaction();
    zeebeDbTransaction.run(
        () -> {
          final long position = typedEvent.getPosition();
          resetOutput(position);

          writeRejectionOnCommand(processingException);
          errorRecord.initErrorRecord(processingException, position);

          zeebeState
              .getBlackListState()
              .tryToBlacklist(typedEvent, errorRecord::setProcessInstanceKey);

          logStreamWriter.appendFollowUpEvent(
              typedEvent.getKey(), ErrorIntent.CREATED, errorRecord);
        });
  }

  private void writeRejectionOnCommand(final Throwable exception) {
    final String errorMessage =
        String.format(PROCESSING_ERROR_MESSAGE, typedEvent, exception.getMessage());
    LOG.error(errorMessage, exception);

    if (typedEvent.getRecordType() == RecordType.COMMAND) {
      logStreamWriter.appendRejection(typedEvent, RejectionType.PROCESSING_ERROR, errorMessage);
      responseWriter.writeRejectionOnCommand(
          typedEvent, RejectionType.PROCESSING_ERROR, errorMessage);
    }
  }

  private void writeEvent() {
    final ActorFuture<Boolean> retryFuture =
        writeRetryStrategy.runWithRetry(
            () -> {
              final long position = logStreamWriter.flush();

              // only overwrite position if events were flushed
              if (position > 0) {
                writtenEventPosition = position;
              }

              return position >= 0;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, t) -> {
          if (t != null) {
            LOG.error(ERROR_MESSAGE_WRITE_EVENT_ABORTED, currentEvent, metadata, t);
            onError(t, this::writeEvent);
          } else {
            // We write various type of records. The positions are always increasing and
            // incremented by 1 for one record (even in a batch), so we can count the amount
            // of written events via the lastWritten and now written position.
            final var amount = writtenEventPosition - lastWrittenEventPosition;
            metrics.recordsWritten(amount);
            updateState();
          }
        });
  }

  private void updateState() {
    final ActorFuture<Boolean> retryFuture =
        updateStateRetryStrategy.runWithRetry(
            () -> {
              zeebeDbTransaction.commit();

              // needs to be directly after commit
              // so no other ActorJob can interfere between commit and update the positions
              if (onErrorHandling) {
                errorRecordPosition = writtenEventPosition;
                logStream
                    .getCommitPositionAsync()
                    .onComplete(
                        (commitPosition, error) -> {
                          if (error == null) {
                            LOG.info(LOG_ERROR_EVENT_WRITTEN, errorRecordPosition, commitPosition);
                          }
                        });
              }
              lastSuccessfulProcessedEventPosition = currentEvent.getPosition();
              metrics.setLastProcessedPosition(lastSuccessfulProcessedEventPosition);
              lastWrittenEventPosition = writtenEventPosition;
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(ERROR_MESSAGE_UPDATE_STATE_FAILED, currentEvent, metadata, throwable);
            onError(throwable, this::updateState);
          } else {
            executeSideEffects();
          }
        });
  }

  private void executeSideEffects() {
    final ActorFuture<Boolean> retryFuture =
        sideEffectsRetryStrategy.runWithRetry(sideEffectProducer::flush, abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(ERROR_MESSAGE_EXECUTE_SIDE_EFFECT_ABORTED, currentEvent, metadata, throwable);
          }

          notifyProcessedListener(typedEvent);

          metrics.processingDuration(
              metadata.getRecordType(), processingStartTime, ActorClock.currentTimeMillis());
          // continue with next event
          currentProcessor = null;
          actor.submit(this::readNextEvent);
        });
  }

  private void notifyProcessedListener(final TypedRecord processedRecord) {
    try {
      onProcessedListener.accept(processedRecord);
    } catch (final Exception e) {
      LOG.error(NOTIFY_PROCESSED_LISTENER_ERROR_MESSAGE, processedRecord, e);
    }
  }

  private void notifySkippedListener(final LoggedEvent skippedRecord) {
    try {
      onSkippedListener.accept(skippedRecord);
    } catch (final Exception e) {
      LOG.error(NOTIFY_SKIPPED_LISTENER_ERROR_MESSAGE, skippedRecord, metadata, e);
    }
  }

  public long getLastSuccessfulProcessedEventPosition() {
    return lastSuccessfulProcessedEventPosition;
  }

  public long getLastWrittenEventPosition() {
    return lastWrittenEventPosition;
  }

  public boolean isMakingProgress() {
    return !onErrorHandlingLoop;
  }

  public void startProcessing(final long lastReprocessedPosition) {
    if (lastSuccessfulProcessedEventPosition == StreamProcessor.UNSET_POSITION) {
      lastSuccessfulProcessedEventPosition = lastReprocessedPosition;
    }
    actor.submit(this::readNextEvent);
  }
}
