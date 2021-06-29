/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.ReprocessingStreamWriter;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.KeyGeneratorControls;
import io.camunda.zeebe.engine.state.mutable.MutableLastProcessedPositionState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.retry.EndlessRetryStrategy;
import io.camunda.zeebe.util.retry.RetryStrategy;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
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

  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;
  private static final String ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT =
      "Expected to find event processor for event '{} {}', but caught an exception. Skip this event.";
  private static final String ERROR_MESSAGE_REPLAY_NO_FOLLOW_UP_EVENT =
      "Expected to find last follow-up event position '%d', but last position was '%d'. Failed to replay on processor";
  private static final String ERROR_MESSAGE_REPLAY_NO_NEXT_EVENT =
      "Expected to find last follow-up event position '%d', but found no next event. Failed to replay on processor";
  private static final String LOG_STMT_REPLAY_FINISHED =
      "Processor finished replay at event position {}";
  private static final String LOG_STMT_FAILED_ON_REPLAY =
      "Event {} failed on replay last time, will call #onError to update process instance blacklist.";
  private static final String ERROR_INCONSISTENT_LOG =
      "Expected that position '%d' of current event is higher then position '%d' of last event, but was not. Inconsistent log detected!";

  private static final Consumer<Long> NOOP_LONG_CONSUMER = (instanceKey) -> {};

  private static final MetadataFilter REPLAY_FILTER =
      recordMetadata -> recordMetadata.getRecordType() == RecordType.EVENT;

  private final RecordMetadata metadata = new RecordMetadata();
  private final MutableZeebeState zeebeState;
  private final KeyGeneratorControls keyGeneratorControls;
  private final MutableLastProcessedPositionState lastProcessedPositionState;
  private final ActorControl actor;
  private final ErrorRecord errorRecord = new ErrorRecord();
  private final TypedEventImpl typedEvent;

  private final RecordValues recordValues;

  private final EventFilter eventFilter =
      new MetadataEventFilter(new RecordProtocolVersionFilter().and(REPLAY_FILTER));

  private final LogStreamReader logStreamReader;
  private final ReprocessingStreamWriter reprocessingStreamWriter;
  private final EventApplier eventApplier;

  private final TransactionContext transactionContext;
  private final RetryStrategy updateStateRetryStrategy;
  private final RetryStrategy processRetryStrategy;

  private final BooleanSupplier abortCondition;
  private final Set<Long> failedEventPositions = new HashSet<>();
  // current iteration
  private long lastSourceEventPosition;
  private long lastFollowUpEventPosition;
  private long snapshotPosition;
  private long highestRecordKey = -1L;

  private ActorFuture<Long> recoveryFuture;
  private LoggedEvent currentEvent;
  private ZeebeDbTransaction zeebeDbTransaction;

  public ReProcessingStateMachine(final ProcessingContext context) {
    actor = context.getActor();
    logStreamReader = context.getLogStreamReader();
    recordValues = context.getRecordValues();
    transactionContext = context.getTransactionContext();
    zeebeState = context.getZeebeState();
    abortCondition = context.getAbortCondition();
    eventApplier = context.getEventApplier();
    keyGeneratorControls = context.getKeyGeneratorControls();
    lastProcessedPositionState = context.getLastProcessedPositionState();

    typedEvent = new TypedEventImpl(context.getLogStream().getPartitionId());
    updateStateRetryStrategy = new EndlessRetryStrategy(actor);
    processRetryStrategy = new EndlessRetryStrategy(actor);
    reprocessingStreamWriter = context.getReprocessingStreamWriter();
  }

  /**
   * Reprocess the records. It returns the position of the last successfully processed record. If
   * there is nothing processed it returns {@link StreamProcessor#UNSET_POSITION}
   *
   * @return a ActorFuture with last reprocessed position
   */
  ActorFuture<Long> startRecover(final long snapshotPosition) {
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
    } else if (snapshotPosition > 0) {
      recoveryFuture.complete(snapshotPosition);
    } else {
      recoveryFuture.complete(StreamProcessor.UNSET_POSITION);
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

        final var recordKey = newEvent.getKey();
        // records from other partitions should not influence the key generator of this partition
        if (Protocol.decodePartitionId(recordKey) == zeebeState.getPartitionId()) {
          // remember the highest key on the stream to restore the key generator after replay
          highestRecordKey = Math.max(recordKey, highestRecordKey);
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
          String.format(ERROR_MESSAGE_REPLAY_NO_NEXT_EVENT, lastFollowUpEventPosition));
    }

    currentEvent = logStreamReader.next();
    if (currentEvent.getPosition() > lastFollowUpEventPosition) {
      throw new IllegalStateException(
          String.format(
              ERROR_MESSAGE_REPLAY_NO_FOLLOW_UP_EVENT,
              lastFollowUpEventPosition,
              currentEvent.getPosition()));
    }
  }

  private void reprocessNextEvent() {
    try {
      readNextEvent();

      if (eventFilter.applies(currentEvent)) {
        reprocessEvent(currentEvent);
      } else {
        onRecordReprocessed(currentEvent);
      }

    } catch (final RuntimeException e) {
      final var processingException =
          new ProcessingException("Unable to replay record", currentEvent, metadata, e);
      recoveryFuture.completeExceptionally(processingException);
    }
  }

  private void reprocessEvent(final LoggedEvent currentEvent) {

    try {
      metadata.reset();
      currentEvent.readMetadata(metadata);
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT, currentEvent, metadata, e);
    }

    final UnifiedRecordValue value =
        recordValues.readRecordValue(currentEvent, metadata.getValueType());
    typedEvent.wrap(currentEvent, metadata, value);

    processUntilDone(currentEvent.getPosition(), typedEvent);
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
              zeebeDbTransaction = transactionContext.getCurrentTransaction();
              zeebeDbTransaction.run(operationOnProcessing);
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        resultFuture,
        (v, t) -> {
          // processing should be retried endless until it worked
          assert t == null : "On replay there shouldn't be any exception thrown.";
          updateStateUntilDone();
        });
  }

  private TransactionOperation chooseOperationForEvent(
      final long position, final TypedRecord<?> currentEvent) {
    final TransactionOperation operationOnProcessing;

    if (failedEventPositions.contains(position)) {
      LOG.info(LOG_STMT_FAILED_ON_REPLAY, currentEvent);
      operationOnProcessing =
          () -> zeebeState.getBlackListState().tryToBlacklist(currentEvent, NOOP_LONG_CONSUMER);
    } else {
      operationOnProcessing =
          () -> {
            final boolean isNotOnBlacklist =
                !zeebeState.getBlackListState().isOnBlacklist(typedEvent);
            if (isNotOnBlacklist) {
              reprocessRecord(currentEvent);
            }
            lastProcessedPositionState.markAsProcessed(position);
          };
    }
    return operationOnProcessing;
  }

  private void reprocessRecord(final TypedRecord<?> currentEvent) {
    // skip events if the state changes are already applied to the state in the snapshot
    if (currentEvent.getSourceRecordPosition() > snapshotPosition) {
      eventApplier.applyState(
          currentEvent.getKey(), currentEvent.getIntent(), currentEvent.getValue());
    }
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
          assert throwable == null : "On replay there shouldn't be any exception thrown.";
          onRecordReprocessed(currentEvent);
        });
  }

  private void onRecordReprocessed(final LoggedEvent currentEvent) {
    reprocessingStreamWriter.removeRecord(
        currentEvent.getKey(), currentEvent.getSourceEventPosition());

    // do reprocessing until the last source event but read until the last follow-up event to check
    // for inconsistent reprocessing records
    if (currentEvent.getPosition() >= lastFollowUpEventPosition) {
      LOG.info(LOG_STMT_REPLAY_FINISHED, currentEvent.getPosition());

      // reset the position to the first event where the processing should start
      logStreamReader.seekToNextEvent(lastSourceEventPosition);

      onRecovered(lastSourceEventPosition);
    } else {
      actor.submit(this::reprocessNextEvent);
    }
  }

  private void onRecovered(final long lastProcessedPosition) {
    keyGeneratorControls.setKeyIfHigher(highestRecordKey);

    failedEventPositions.clear();

    recoveryFuture.complete(lastProcessedPosition);
  }
}
