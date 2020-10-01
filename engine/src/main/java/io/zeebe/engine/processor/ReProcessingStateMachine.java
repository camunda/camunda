/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.db.DbContext;
import io.zeebe.db.TransactionOperation;
import io.zeebe.db.ZeebeDbTransaction;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.util.retry.EndlessRetryStrategy;
import io.zeebe.util.retry.RetryStrategy;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Represents the reprocessing state machine, which is executed on reprocessing.
 *
 * <pre>
 * +------------------+   +-------------+           +------------------------+
 * |                  |   |             |           |                        |
 * |  startRecover()  |--->  scanLog()  |---------->|  reprocessNextEvent()  |
 * |                  |   |             |           |                        |
 * +------------------+   +---+---------+           +-----^------+-----------+
 *                            |                           |      |
 * +-----------------+        | no source events          |      |
 * |                 |        |                           |      |
 * |  onRecovered()  <--------+                           |      |    +--------------------+
 * |                 |                                    |      |    |                    |
 * +--------^--------+                hasNext             |      +--->|  reprocessEvent()  |
 *          |            +--------------------------------+           |                    |
 *          |            |                                            +----+----------+----+
 *          |            |                                                 |          |
 *   +------+------------+-----+                                           |          |
 *   |                         |               no event processor          |          |
 *   |  onRecordReprocessed()  |<------------------------------------------+          |
 *   |                         |                                                      |
 *   +---------^---------------+                                                      |
 *             |                                                                      |
 *             |      +--------------------------+       +----------------------+     |
 *             |      |                          |       |                      |     |
 *             +------+  updateStateUntilDone()  <-------+  processUntilDone()  |<----+
 *                    |                          |       |                      |
 *                    +------^------------+------+       +---^------------+-----+
 *                           |            |                  |            |
 *                           +------------+                  +------------+
 *                             exception                       exception
 * </pre>
 *
 * See https://textik.com/#773271ce7ea2096a
 */
public final class ReProcessingStateMachine {

  public static final Consumer NOOP_SIDE_EFFECT_CONSUMER = (sideEffect) -> {};
  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;
  private static final String ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT =
      "Expected to find event processor for event '{}', but caught an exception. Skip this event.";
  private static final String ERROR_MESSAGE_REPROCESSING_NO_FOLLOW_UP_EVENT =
      "Expected to find last follow-up event position '%d', but last position was '%d'. Failed to reprocess on processor";
  private static final String ERROR_MESSAGE_REPROCESSING_NO_NEXT_EVENT =
      "Expected to find last follow-up event position '%d', but found no next event. Failed to reprocess on processor";
  private static final String LOG_STMT_REPROCESSING_FINISHED =
      "Processor finished reprocessing at event position {}";
  private static final String LOG_STMT_FAILED_ON_PROCESSING =
      "Event {} failed on processing last time, will call #onError to update workflow instance blacklist.";

  private static final String ERROR_INCONSISTENT_LOG =
      "Expected that position '%d' of current event is higher then position '%d' of last event, but was not. Inconsistent log detected!";

  private static final Consumer<Long> NOOP_LONG_CONSUMER = (instanceKey) -> {};
  protected final RecordMetadata metadata = new RecordMetadata();
  private final ZeebeState zeebeState;
  private final ActorControl actor;
  private final ErrorRecord errorRecord = new ErrorRecord();
  private final TypedEventImpl typedEvent;

  private final RecordValues recordValues;
  private final RecordProcessorMap recordProcessorMap;

  private final EventFilter eventFilter;
  private final LogStreamReader logStreamReader;
  private final ReprocessingStreamWriter reprocessingStreamWriter = new ReprocessingStreamWriter();
  private final TypedResponseWriter noopResponseWriter = new NoopResponseWriter();

  private final DbContext dbContext;
  private final RetryStrategy updateStateRetryStrategy;
  private final RetryStrategy processRetryStrategy;

  private final BooleanSupplier abortCondition;
  private final Set<Long> failedEventPositions = new HashSet<>();
  // current iteration
  private long lastSourceEventPosition;
  private ActorFuture<Void> recoveryFuture;
  private long lastFollowUpEventPosition;
  private long snapshotPosition;

  private LoggedEvent currentEvent;
  private TypedRecordProcessor eventProcessor;
  private ZeebeDbTransaction zeebeDbTransaction;

  public ReProcessingStateMachine(final ProcessingContext context) {
    actor = context.getActor();
    eventFilter = context.getEventFilter();
    logStreamReader = context.getLogStreamReader();
    recordValues = context.getRecordValues();
    recordProcessorMap = context.getRecordProcessorMap();
    dbContext = context.getDbContext();
    zeebeState = context.getZeebeState();
    abortCondition = context.getAbortCondition();
    typedEvent = new TypedEventImpl(context.getLogStream().getPartitionId());

    updateStateRetryStrategy = new EndlessRetryStrategy(actor);
    processRetryStrategy = new EndlessRetryStrategy(actor);
  }

  ActorFuture<Void> startRecover(final long snapshotPosition) {
    recoveryFuture = new CompletableActorFuture<>();

    this.snapshotPosition = snapshotPosition;

    LOG.trace("Start scanning the log for error events.");
    lastSourceEventPosition = scanLog(snapshotPosition);
    LOG.trace("Finished scanning the log for error events.");

    if (lastSourceEventPosition > snapshotPosition) {
      LOG.info(
          "Processor starts reprocessing, until last source event position {}",
          lastSourceEventPosition);
      logStreamReader.seekToNextEvent(snapshotPosition);
      reprocessNextEvent();
    } else {
      recoveryFuture.complete(null);
    }
    return recoveryFuture;
  }

  private long scanLog(final long snapshotPosition) {
    long lastSourceEventPosition = -1L;

    if (logStreamReader.hasNext()) {
      lastSourceEventPosition = snapshotPosition;

      long lastPosition = snapshotPosition;
      while (logStreamReader.hasNext()) {
        final LoggedEvent newEvent = logStreamReader.next();

        final var currentPosition = newEvent.getPosition();
        if (lastPosition >= currentPosition) {
          throw new IllegalStateException(
              String.format(ERROR_INCONSISTENT_LOG, currentPosition, lastPosition));
        }
        lastPosition = currentPosition;

        metadata.reset();
        newEvent.readMetadata(metadata);
        long errorPosition = -1;
        if (metadata.getValueType() == ValueType.ERROR) {
          newEvent.readValue(errorRecord);
          errorPosition = errorRecord.getErrorEventPosition();
        }

        if (errorPosition >= 0) {
          LOG.debug(
              "Found error-prone event {} on reprocessing, will add position {} to the blacklist.",
              newEvent,
              errorPosition);
          failedEventPositions.add(errorPosition);
        }

        final long sourceEventPosition = newEvent.getSourceEventPosition();
        if (sourceEventPosition > 0) {
          if (sourceEventPosition > lastSourceEventPosition) {
            lastSourceEventPosition = sourceEventPosition;
          }
          if (currentPosition > lastFollowUpEventPosition) {
            lastFollowUpEventPosition = currentPosition;
          }
        }
      }

      // reset position
      logStreamReader.seek(snapshotPosition + 1);
    }

    return lastSourceEventPosition;
  }

  private void readNextEvent() {
    if (!logStreamReader.hasNext()) {
      throw new IllegalStateException(
          String.format(ERROR_MESSAGE_REPROCESSING_NO_NEXT_EVENT, lastFollowUpEventPosition));
    }

    currentEvent = logStreamReader.next();
    if (currentEvent.getPosition() > lastFollowUpEventPosition) {
      throw new IllegalStateException(
          String.format(
              ERROR_MESSAGE_REPROCESSING_NO_FOLLOW_UP_EVENT,
              lastFollowUpEventPosition,
              currentEvent.getPosition()));
    }
  }

  private void reprocessNextEvent() {
    try {
      readNextEvent();

      if (eventFilter == null || eventFilter.applies(currentEvent)) {
        reprocessEvent(currentEvent);
      } else {
        onRecordReprocessed(currentEvent);
      }

    } catch (final RuntimeException e) {
      recoveryFuture.completeExceptionally(e);
    }
  }

  private void reprocessEvent(final LoggedEvent currentEvent) {

    try {
      metadata.reset();
      currentEvent.readMetadata(metadata);

      eventProcessor =
          recordProcessorMap.get(
              metadata.getRecordType(), metadata.getValueType(), metadata.getIntent().value());
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT, currentEvent, e);
    }

    if (eventProcessor == null) {
      onRecordReprocessed(currentEvent);
      return;
    }

    final UnifiedRecordValue value =
        recordValues.readRecordValue(currentEvent, metadata.getValueType());
    typedEvent.wrap(currentEvent, metadata, value);

    verifyRecordMatchesToReprocessing(typedEvent);

    if (currentEvent.getPosition() <= lastSourceEventPosition) {
      // don't reprocess records after the last source event
      reprocessingStreamWriter.configureSourceContext(currentEvent.getPosition());
      processUntilDone(currentEvent.getPosition(), typedEvent);

    } else {
      onRecordReprocessed(currentEvent);
    }
  }

  private void processUntilDone(final long position, final TypedRecord<?> currentEvent) {
    final TransactionOperation operationOnProcessing =
        chooseOperationForEvent(position, currentEvent);

    final ActorFuture<Boolean> resultFuture =
        processRetryStrategy.runWithRetry(
            () -> {
              final boolean onRetry = zeebeDbTransaction != null;
              if (onRetry) {
                zeebeDbTransaction.rollback();
              }
              zeebeDbTransaction = dbContext.getCurrentTransaction();
              zeebeDbTransaction.run(operationOnProcessing);
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        resultFuture,
        (v, t) -> {
          // processing should be retried endless until it worked
          assert t == null : "On reprocessing there shouldn't be any exception thrown.";
          updateStateUntilDone();
        });
  }

  private TransactionOperation chooseOperationForEvent(
      final long position, final TypedRecord<?> currentEvent) {
    final TransactionOperation operationOnProcessing;
    if (failedEventPositions.contains(position)) {
      LOG.info(LOG_STMT_FAILED_ON_PROCESSING, currentEvent);
      operationOnProcessing = () -> zeebeState.tryToBlacklist(currentEvent, NOOP_LONG_CONSUMER);
    } else {
      operationOnProcessing =
          () -> {
            final boolean isNotOnBlacklist = !zeebeState.isOnBlacklist(typedEvent);
            if (isNotOnBlacklist) {
              eventProcessor.processRecord(
                  position,
                  typedEvent,
                  noopResponseWriter,
                  reprocessingStreamWriter,
                  NOOP_SIDE_EFFECT_CONSUMER);
            }
            zeebeState.markAsProcessed(position);
          };
    }
    return operationOnProcessing;
  }

  private void updateStateUntilDone() {
    final ActorFuture<Boolean> retryFuture =
        updateStateRetryStrategy.runWithRetry(
            () -> {
              zeebeDbTransaction.commit();
              zeebeDbTransaction = null;
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          // update state should be retried endless until it worked
          assert throwable == null : "On reprocessing there shouldn't be any exception thrown.";
          onRecordReprocessed(currentEvent);
        });
  }

  private void onRecordReprocessed(final LoggedEvent currentEvent) {
    reprocessingStreamWriter.removeRecord(
        currentEvent.getKey(), currentEvent.getSourceEventPosition());

    // do reprocessing until the last source event but read until the last follow-up event to check
    // for inconsistent reprocessing records
    if (currentEvent.getPosition() >= lastFollowUpEventPosition) {
      LOG.info(LOG_STMT_REPROCESSING_FINISHED, currentEvent.getPosition());

      // reset the position to the first event where the processing should start
      logStreamReader.seekToNextEvent(lastSourceEventPosition);

      onRecovered();
    } else {
      actor.submit(this::reprocessNextEvent);
    }
  }

  private void onRecovered() {
    recoveryFuture.complete(null);
    failedEventPositions.clear();
  }

  private void verifyRecordMatchesToReprocessing(final TypedRecord<?> currentEvent) {

    if (currentEvent.getSourceRecordPosition() < 0
        || currentEvent.getSourceRecordPosition() <= snapshotPosition) {
      // ignore commands (i.e. no source currentEvent position) and records that are not produced by
      // the reprocessing (i.e. the source currentEvent is already compacted)
      return;
    }

    // if a record is not written to the log stream then the state could be corrupted
    reprocessingStreamWriter.getRecords().stream()
        .filter(record -> record.getSourceRecordPosition() < currentEvent.getSourceRecordPosition())
        .findFirst()
        .ifPresent(
            missingRecordOnLogStream -> {
              throw new InconsistentReprocessingException(
                  "Records were created on reprocessing but not written on the log stream.",
                  typedEvent,
                  missingRecordOnLogStream);
            });

    // If the record was not written on reprocessing then the next record may have a different key,
    // or the state is corrupted. But since the source record position can be wrong (#5420), we can
    // not fail the reprocessing at the moment.

    reprocessingStreamWriter.getRecords().stream()
        .filter(
            record -> record.getSourceRecordPosition() == currentEvent.getSourceRecordPosition())
        .findFirst()
        .ifPresent(
            reprocessingRecord -> {

              // compare the key and the intent of the record with the record that was written on
              // reprocessing
              if (reprocessingRecord.getKey() != currentEvent.getKey()) {
                throw new InconsistentReprocessingException(
                    "The key of the record on the log stream doesn't match to the record from reprocessing.",
                    typedEvent,
                    reprocessingRecord);
              }

              if (reprocessingRecord.getIntent() != currentEvent.getIntent()) {
                throw new InconsistentReprocessingException(
                    "The intent of the record on the log stream doesn't match to the record from reprocessing.",
                    typedEvent,
                    reprocessingRecord);
              }
            });
  }
}
