/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDbTransaction;
import io.zeebe.engine.metrics.StreamProcessorMetrics;
import io.zeebe.engine.state.ZeebeState;
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

  public static final String ERROR_MESSAGE_WRITE_EVENT_ABORTED =
      "Expected to write one or more follow up events for event '{}' without errors, but exception was thrown.";
  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;
  private static final String ERROR_MESSAGE_ROLLBACK_ABORTED =
      "Expected to roll back the current transaction for event '{}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_EXECUTE_SIDE_EFFECT_ABORTED =
      "Expected to execute side effects for event '{}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_UPDATE_STATE_FAILED =
      "Expected to successfully update state for event '{}', but caught an exception. Retry.";
  private static final String ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT =
      "Expected to find event processor for event '{}', but caught an exception. Skip this event.";
  private static final String ERROR_MESSAGE_PROCESSING_FAILED_SKIP_EVENT =
      "Expected to successfully process event '{}' with processor, but caught an exception. Skip this event.";
  private static final String ERROR_MESSAGE_PROCESSING_FAILED_RETRY_PROCESSING =
      "Expected to process event '{}' successfully on stream processor, but caught recoverable exception. Retry processing.";
  private static final String PROCESSING_ERROR_MESSAGE =
      "Expected to process event '%s' without errors, but exception occurred with message '%s' .";
  private static final String NOTIFY_LISTENER_ERROR_MESSAGE =
      "Expected to invoke processed listener for event {} successfully, but exception was thrown.";

  private static final String LOG_ERROR_EVENT_COMMITTED =
      "Error event was committed, we continue with processing.";
  private static final String LOG_ERROR_EVENT_WRITTEN =
      "Error record was written at {}, we will continue with processing if event was committed. Current commit position is {}.";

  private static final Duration PROCESSING_RETRY_DELAY = Duration.ofMillis(250);
  protected final ZeebeState zeebeState;
  protected final RecordMetadata metadata = new RecordMetadata();
  protected final TypedResponseWriterImpl responseWriter;
  private final ActorControl actor;
  private final EventFilter eventFilter;
  private final LogStream logStream;
  private final LogStreamReader logStreamReader;
  private final TypedStreamWriter logStreamWriter;
  private final DbContext dbContext;
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
  private final Consumer<TypedRecord> onProcessed;

  // current iteration
  private SideEffectProducer sideEffectProducer;
  private LoggedEvent currentEvent;
  private TypedRecordProcessor<?> currentProcessor;
  private ZeebeDbTransaction zeebeDbTransaction;
  private long writtenEventPosition = -1L;
  private long lastSuccessfulProcessedEventPosition = -1L;
  private long lastWrittenEventPosition = -1L;
  private boolean onErrorHandling;
  private long errorRecordPosition = -1;

  public ProcessingStateMachine(ProcessingContext context, BooleanSupplier shouldProcessNext) {

    this.actor = context.getActor();
    this.eventFilter = context.getEventFilter();
    this.recordProcessorMap = context.getRecordProcessorMap();
    this.recordValues = context.getRecordValues();
    this.logStreamReader = context.getLogStreamReader();
    this.logStreamWriter = context.getLogStreamWriter();
    this.logStream = context.getLogStream();
    this.zeebeState = context.getZeebeState();
    this.dbContext = context.getDbContext();
    this.abortCondition = context.getAbortCondition();

    this.writeRetryStrategy = new AbortableRetryStrategy(actor);
    this.sideEffectsRetryStrategy = new AbortableRetryStrategy(actor);
    this.updateStateRetryStrategy = new RecoverableRetryStrategy(actor);
    this.shouldProcessNext = shouldProcessNext;

    final int partitionId = logStream.getPartitionId();
    this.typedEvent = new TypedEventImpl(partitionId);
    this.responseWriter =
        new TypedResponseWriterImpl(context.getCommandResponseWriter(), partitionId);

    this.metrics = new StreamProcessorMetrics(partitionId);
    this.onProcessed = context.getOnProcessedListener();
  }

  private void skipRecord() {
    actor.submit(this::readNextEvent);
    metrics.eventSkipped();
  }

  void readNextEvent() {
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

      if (eventFilter == null || eventFilter.applies(currentEvent)) {
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

    metrics.processingLatency(
        metadata.getRecordType(), event.getTimestamp(), ActorClock.currentTimeMillis());

    try {
      final UnifiedRecordValue value = recordValues.readRecordValue(event, metadata.getValueType());
      typedEvent.wrap(event, metadata, value);

      processInTransaction(typedEvent);

      metrics.eventProcessed();

      writeEvent();
    } catch (final RecoverableException recoverableException) {
      // recoverable
      LOG.error(ERROR_MESSAGE_PROCESSING_FAILED_RETRY_PROCESSING, event, recoverableException);
      actor.runDelayed(PROCESSING_RETRY_DELAY, () -> processEvent(currentEvent));
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_PROCESSING_FAILED_SKIP_EVENT, event, e);
      onError(e, this::writeEvent);
    }
  }

  private TypedRecordProcessor<?> chooseNextProcessor(LoggedEvent event) {
    TypedRecordProcessor<?> typedRecordProcessor = null;

    try {
      typedRecordProcessor =
          recordProcessorMap.get(
              metadata.getRecordType(), metadata.getValueType(), metadata.getIntent().value());
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT, event, e);
    }

    return typedRecordProcessor;
  }

  private void processInTransaction(final TypedEventImpl typedRecord) throws Exception {
    zeebeDbTransaction = dbContext.getCurrentTransaction();
    zeebeDbTransaction.run(
        () -> {
          final long position = typedRecord.getPosition();
          resetOutput(position);

          // default side effect is responses; can be changed by processor
          sideEffectProducer = responseWriter;
          final boolean isNotOnBlacklist = !zeebeState.isOnBlacklist(typedRecord);
          if (isNotOnBlacklist) {
            currentProcessor.processRecord(
                position,
                typedRecord,
                responseWriter,
                logStreamWriter,
                this::setSideEffectProducer);
          }

          zeebeState.markAsProcessed(position);
        });
  }

  private void resetOutput(long sourceRecordPosition) {
    responseWriter.reset();
    logStreamWriter.reset();
    logStreamWriter.configureSourceContext(sourceRecordPosition);
  }

  public void setSideEffectProducer(final SideEffectProducer sideEffectProducer) {
    this.sideEffectProducer = sideEffectProducer;
  }

  private void onError(Throwable processingException, Runnable nextStep) {
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
            LOG.error(ERROR_MESSAGE_ROLLBACK_ABORTED, currentEvent, throwable);
          }
          try {
            errorHandlingInTransaction(processingException);

            onErrorHandling = true;
            nextStep.run();
          } catch (Exception ex) {
            onError(ex, nextStep);
          }
        });
  }

  private void errorHandlingInTransaction(Throwable processingException) throws Exception {
    zeebeDbTransaction = dbContext.getCurrentTransaction();
    zeebeDbTransaction.run(
        () -> {
          final long position = typedEvent.getPosition();
          resetOutput(position);

          writeRejectionOnCommand(processingException);
          errorRecord.initErrorRecord(processingException, position);

          zeebeState.tryToBlacklist(typedEvent, errorRecord::setWorkflowInstanceKey);

          logStreamWriter.appendFollowUpEvent(
              typedEvent.getKey(), ErrorIntent.CREATED, errorRecord);
        });
  }

  private void writeRejectionOnCommand(Throwable exception) {
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
              writtenEventPosition = logStreamWriter.flush();
              return writtenEventPosition >= 0;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, t) -> {
          if (t != null) {
            LOG.error(ERROR_MESSAGE_WRITE_EVENT_ABORTED, currentEvent, t);
            onError(t, this::writeEvent);
          } else {
            updateState();
            metrics.eventWritten();
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
              lastWrittenEventPosition = writtenEventPosition;
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(ERROR_MESSAGE_UPDATE_STATE_FAILED, currentEvent, throwable);
            onError(throwable, this::updateState);
          } else {
            executeSideEffects();
          }
        });
  }

  private void notifyListener() {
    try {
      onProcessed.accept(typedEvent);
    } catch (Exception e) {
      LOG.error(NOTIFY_LISTENER_ERROR_MESSAGE, currentEvent, e);
    }
  }

  private void executeSideEffects() {
    final ActorFuture<Boolean> retryFuture =
        sideEffectsRetryStrategy.runWithRetry(sideEffectProducer::flush, abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(ERROR_MESSAGE_EXECUTE_SIDE_EFFECT_ABORTED, currentEvent, throwable);
          }

          notifyListener();

          // continue with next event
          currentProcessor = null;
          actor.submit(this::readNextEvent);
        });
  }

  public long getLastSuccessfulProcessedEventPosition() {
    return lastSuccessfulProcessedEventPosition;
  }

  public long getLastWrittenEventPosition() {
    return lastWrittenEventPosition;
  }
}
