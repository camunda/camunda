/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbTransaction;
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
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.util.retry.EndlessRetryStrategy;
import io.camunda.zeebe.util.retry.RetryStrategy;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/**
 * Represents the state machine to replay events.
 *
 * <pre>
 * +------------------+   +-------------+           +------------------------+
 * |                  |   |             |           |                        |
 * |  startRecover()  |--->  scanLog()  |---------->|  replayNextEvent()     |
 * |                  |   |             |           |                        |
 * +------------------+   +---+---------+           +-----^------+-----------+
 *                            |                           |      |
 * +-----------------+        | no source events          |      |
 * |                 |        |                           |      |
 * |  onRecovered()  <--------+                           |      |    +--------------------+
 * |                 |                                    |      |    |                    |
 * +--------^--------+                hasNext             |      +--->|  replayEvent()     |
 *          |            +--------------------------------+           |                    |
 *          |            |                                            +----+----------+----+
 *          |            |                                                 |          |
 *   +------+------------+-----+                                           |          |
 *   |                         |               no event processor          |          |
 *   |  onRecordReplayed()     |<------------------------------------------+          |
 *   |                         |                                                      |
 *   +---------^---------------+                                                      |
 *             |                                                                      |
 *             |      +--------------------------+       +----------------------+     |
 *             |      |                          |       |                      |     |
 *             +------+  updateStateUntilDone()  <-------+  replayUntilDone()   |<----+
 *                    |                          |       |                      |
 *                    +------^------------+------+       +---^------------+-----+
 *                           |            |                  |            |
 *                           +------------+                  +------------+
 *                             exception                       exception
 * </pre>
 *
 * See https://textik.com/#773271ce7ea2096a
 */
public final class ReplayStateMachine {

  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;
  private static final String LOG_STMT_REPLAY_FINISHED =
      "Processor finished replay at event position {}";
  private static final String ERROR_INCONSISTENT_LOG =
      "Expected that position '%d' of current event is higher then position '%d' of last event, but was not. Inconsistent log detected!";

  private static final MetadataFilter REPLAY_FILTER =
      recordMetadata -> recordMetadata.getRecordType() == RecordType.EVENT;
  private static final String ERROR_MSG_EXPECTED_TO_READ_METADATA =
      "Expected to read the metadata for the record '%s', but an exception was thrown.";

  private final RecordMetadata metadata = new RecordMetadata();
  private final MutableZeebeState zeebeState;
  private final KeyGeneratorControls keyGeneratorControls;
  private final MutableLastProcessedPositionState lastProcessedPositionState;
  private final ActorControl actor;
  private final TypedEventImpl typedEvent;

  private final RecordValues recordValues;

  private final EventFilter eventFilter =
      new MetadataEventFilter(new RecordProtocolVersionFilter().and(REPLAY_FILTER));

  private final LogStreamReader logStreamReader;
  private final EventApplier eventApplier;

  private final TransactionContext transactionContext;
  private final RetryStrategy updateStateRetryStrategy;
  private final RetryStrategy processRetryStrategy;

  private final BooleanSupplier abortCondition;
  // current iteration
  private long lastSourceEventPosition;
  private long snapshotPosition;
  private long highestRecordKey = -1L;
  private long lastPosition;

  private ActorFuture<Long> recoveryFuture;
  private LoggedEvent currentEvent;
  private ZeebeDbTransaction zeebeDbTransaction;

  public ReplayStateMachine(final ProcessingContext context) {
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
  }

  /**
   * Replay events on the log stream to restore the state. It returns the position of the last
   * command that was processed on the stream. If no command was processed it returns {@link
   * StreamProcessor#UNSET_POSITION}.
   *
   * @return a ActorFuture with the position of the last processed command
   */
  ActorFuture<Long> startRecover(final long snapshotPosition) {
    recoveryFuture = new CompletableActorFuture<>();
    this.snapshotPosition = snapshotPosition;
    lastSourceEventPosition = snapshotPosition;

    // start after snapshot
    logStreamReader.seekToNextEvent(snapshotPosition);

    if (logStreamReader.hasNext()) {
      LOG.info("Processor starts replay of events. [snapshot-position: {}]", snapshotPosition);
      replayNextEvent();
    } else if (snapshotPosition > 0) {
      recoveryFuture.complete(snapshotPosition);
    } else {
      recoveryFuture.complete(StreamProcessor.UNSET_POSITION);
    }

    return recoveryFuture;
  }

  private void replayNextEvent() {
    try {

      if (!logStreamReader.hasNext()) {
        // Replay ends at the end of the log
        // reset the position to the first event where the processing should start
        // TODO(zell): this should probably done outside; makes no sense here
        logStreamReader.seekToNextEvent(lastSourceEventPosition);

        // restore the key generate with the highest key from the log
        keyGeneratorControls.setKeyIfHigher(highestRecordKey);

        LOG.info(LOG_STMT_REPLAY_FINISHED, lastPosition);
        recoveryFuture.complete(lastSourceEventPosition);
        return;
      }

      currentEvent = logStreamReader.next();
      if (eventFilter.applies(currentEvent)) {

        if (!readMetadata()) {
          // failure on reading metadata
          return;
        }

        final UnifiedRecordValue value =
            recordValues.readRecordValue(currentEvent, metadata.getValueType());
        typedEvent.wrap(currentEvent, metadata, value);

        processRetryStrategy
            .runWithRetry(
                () -> {
                  final boolean onRetry = zeebeDbTransaction != null;
                  if (onRetry) {
                    zeebeDbTransaction.rollback();
                  }

                  zeebeDbTransaction = transactionContext.getCurrentTransaction();
                  zeebeDbTransaction.run(
                      () -> {
                        if (typedEvent.getSourceRecordPosition() > snapshotPosition) {
                          eventApplier.applyState(
                              typedEvent.getKey(), typedEvent.getIntent(), typedEvent.getValue());
                        }
                        lastProcessedPositionState.markAsProcessed(currentEvent.getPosition());
                      });

                  return true;
                },
                abortCondition)
            .onComplete(
                (v, t) -> {
                  // replay should be retried endless until it worked
                  assert t == null : "On replay there shouldn't be any exception thrown.";
                  updateStateRetryStrategy
                      .runWithRetry(
                          () -> {
                            zeebeDbTransaction.commit();
                            zeebeDbTransaction = null;
                            return true;
                          },
                          abortCondition)
                      .onComplete(
                          (bool, throwable) -> {
                            // update state should be retried endless until it worked
                            assert throwable == null
                                : "On replay there shouldn't be any exception thrown.";
                            onRecordReplayed(currentEvent);
                          });
                });
      } else {
        onRecordReplayed(currentEvent);
      }

    } catch (final RuntimeException e) {
      final var replayException =
          new ProcessingException("Unable to replay record", currentEvent, metadata, e);
      recoveryFuture.completeExceptionally(replayException);
    }
  }

  private boolean readMetadata() {
    try {
      metadata.reset();
      currentEvent.readMetadata(metadata);
    } catch (final Exception e) {
      final var errorMsg = String.format(ERROR_MSG_EXPECTED_TO_READ_METADATA, currentEvent);
      LOG.error(errorMsg, currentEvent, e);
      final var replayException = new ProcessingException(errorMsg, currentEvent, null, e);
      recoveryFuture.completeExceptionally(replayException);
      return false;
    }
    return true;
  }

  private void onRecordReplayed(final LoggedEvent currentEvent) {
    final var sourceEventPosition = currentEvent.getSourceEventPosition();
    final var currentPosition = currentEvent.getPosition();
    final var currentRecordKey = currentEvent.getKey();

    // positions should always increase
    // if this is not the case we have some inconsistency in our log
    if (lastPosition >= currentPosition) {
      throw new IllegalStateException(
          String.format(ERROR_INCONSISTENT_LOG, currentPosition, lastPosition));
    }
    lastPosition = currentPosition;

    // we need to keep track of the last source event position to know where to start with
    // processing after replay
    if (sourceEventPosition > 0) {
      lastSourceEventPosition = sourceEventPosition;
    }

    // records from other partitions should not influence the key generator of this partition
    if (Protocol.decodePartitionId(currentRecordKey) == zeebeState.getPartitionId()) {
      // remember the highest key on the stream to restore the key generator after replay
      highestRecordKey = Math.max(currentRecordKey, highestRecordKey);
    }

    actor.submit(this::replayNextEvent);
  }
}
