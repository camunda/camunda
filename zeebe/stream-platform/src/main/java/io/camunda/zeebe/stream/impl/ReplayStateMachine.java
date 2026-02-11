/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.impl.log.LogStreamBatchReaderImpl;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBatchReader;
import io.camunda.zeebe.logstreams.log.LogStreamBatchReader.Batch;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.retry.RecoverableRetryStrategy;
import io.camunda.zeebe.scheduler.retry.RetryStrategy;
import io.camunda.zeebe.stream.api.EventFilter;
import io.camunda.zeebe.stream.api.MetadataFilter;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGeneratorControls;
import io.camunda.zeebe.stream.api.state.MutableLastProcessedPositionState;
import io.camunda.zeebe.stream.impl.metrics.ReplayMetrics;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.camunda.zeebe.stream.impl.records.TypedRecordImpl;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;

/** Represents the state machine to replay events and rebuild the state. */
public final class ReplayStateMachine implements LogRecordAwaiter {

  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;

  private static final String LOG_STMT_REPLAY_FINISHED = "Processor finished replay, with {}";
  private static final String ERROR_INCONSISTENT_LOG =
      "Expected that position '%d' of current event is higher then position '%d' of last event, but was not. Inconsistent log detected!";
  private static final String ERROR_MSG_EXPECTED_TO_READ_METADATA =
      "Expected to read the metadata for the record '%s', but an exception was thrown.";

  private static final MetadataFilter REPLAY_FILTER =
      recordMetadata -> recordMetadata.getRecordType() == RecordType.EVENT;

  private final RecordMetadata metadata = new RecordMetadata();
  private final KeyGeneratorControls keyGeneratorControls;
  private final MutableLastProcessedPositionState lastProcessedPositionState;
  private final ActorControl actor;
  private final TypedRecordImpl typedEvent;

  private final RecordValues recordValues;

  private final EventFilter eventFilter = new MetadataEventFilter(REPLAY_FILTER);

  private final LogStreamBatchReader logStreamBatchReader;

  private final TransactionContext transactionContext;
  private final RetryStrategy replayStrategy;

  private final BooleanSupplier abortCondition;
  // current iteration
  private long lastSourceEventPosition = StreamProcessor.UNSET_POSITION;
  private long batchSourceEventPosition = StreamProcessor.UNSET_POSITION;

  private long snapshotPosition;
  private long lastReadRecordPosition = StreamProcessor.UNSET_POSITION;
  private long lastReplayedEventPosition = StreamProcessor.UNSET_POSITION;

  private ActorFuture<LastProcessingPositions> recoveryFuture;
  private ZeebeDbTransaction zeebeDbTransaction;
  private final StreamProcessorMode streamProcessorMode;
  private final LogStream logStream;

  private State currentState = State.AWAIT_RECORD;
  private final BooleanSupplier shouldPause;
  private final ReplayMetrics replayMetrics;
  private final List<RecordProcessor> recordProcessors;
  private final int partitionId;
  private volatile Supplier<String> currentStateDescription = () -> "idle";

  public ReplayStateMachine(
      final List<RecordProcessor> recordProcessors,
      final StreamProcessorContext context,
      final BooleanSupplier shouldReplayNext) {
    partitionId = context.getPartitionId();
    this.recordProcessors = recordProcessors;
    shouldPause = () -> !shouldReplayNext.getAsBoolean();
    actor = context.getActor();
    recordValues = context.getRecordValues();
    transactionContext = context.getTransactionContext();
    abortCondition = context.getAbortCondition();
    keyGeneratorControls = context.getKeyGeneratorControls();
    lastProcessedPositionState = context.getLastProcessedPositionState();

    typedEvent = new TypedRecordImpl(context.getLogStream().getPartitionId());
    replayStrategy = new RecoverableRetryStrategy(actor);
    streamProcessorMode = context.getProcessorMode();
    logStream = context.getLogStream();
    logStreamBatchReader = new LogStreamBatchReaderImpl(context.getLogStreamReader());
    replayMetrics = new ReplayMetrics(context.getMeterRegistry());
  }

  /**
   * Replay events on the log stream to restore the state. It returns the position of the last
   * command that was processed on the stream. If no command was processed it returns {@link
   * StreamProcessor#UNSET_POSITION}.
   *
   * @return a ActorFuture with the position of the last processed command
   */
  ActorFuture<LastProcessingPositions> startRecover(final long snapshotPosition) {
    recoveryFuture = new CompletableActorFuture<>();
    this.snapshotPosition = snapshotPosition;
    lastSourceEventPosition =
        snapshotPosition > 0 ? snapshotPosition : StreamProcessor.UNSET_POSITION;

    // start after snapshot
    logStreamBatchReader.seekToNextBatch(snapshotPosition);

    LOG.info(
        "Processor starts replay of events. [snapshot-position: {}, replay-mode: {}]",
        snapshotPosition,
        streamProcessorMode);

    if (streamProcessorMode == StreamProcessorMode.REPLAY) {
      logStream.registerRecordAvailableListener(this);
    }

    replayNextEvent();

    return recoveryFuture;
  }

  @Override
  public void onRecordAvailable() {
    actor.call(
        () -> {
          if (currentState == State.AWAIT_RECORD) {
            replayNextEvent();
          }
        });
  }

  void replayNextEvent() {
    if (shouldPause.getAsBoolean()) {
      return;
    }

    try {
      if (logStreamBatchReader.hasNext()) {
        currentState = State.REPLAY_EVENT;

        final var replayDurationTimer = replayMetrics.startReplayDurationTimer();
        final var batch = logStreamBatchReader.next();
        replayStrategy
            .runWithRetry(() -> tryToReplayBatch(batch), abortCondition)
            .onComplete(
                (success, failure) -> {
                  if (failure != null) {
                    throw new RuntimeException(
                        "Failed to replay batch at '%s %s'"
                            .formatted(batch.current(), typedEvent.getMetadata()),
                        failure);
                  } else {
                    // observe the replay duration
                    replayDurationTimer.close();
                    // the position should be visible only after the batch is replayed successfully
                    lastSourceEventPosition =
                        Math.max(lastSourceEventPosition, batchSourceEventPosition);
                    replayMetrics.setLastSourcePosition(lastSourceEventPosition);
                    actor.submit(this::replayNextEvent);
                  }
                });

      } else if (streamProcessorMode == StreamProcessorMode.PROCESSING) {
        onRecordsReplayed();

      } else {
        currentStateDescription = () -> "awaiting record to replay";
        currentState = State.AWAIT_RECORD;
      }

    } catch (final RuntimeException e) {
      final var message =
          String.format(
              "Failed to replay records. [snapshot-position: %d, last-read-record-position: %d, last-replayed-event-position: %d]",
              snapshotPosition, lastReadRecordPosition, lastReplayedEventPosition);
      recoveryFuture.completeExceptionally(new RuntimeException(message, e));
    }
  }

  private boolean tryToReplayBatch(final Batch batch) throws Exception {
    final boolean onRetry = zeebeDbTransaction != null;
    if (onRetry) {
      zeebeDbTransaction.rollback();
      // reading the whole batch from the beginning again
      batch.head();
    }

    zeebeDbTransaction = transactionContext.getCurrentTransaction();
    zeebeDbTransaction.run(
        () -> {
          batch.forEachRemaining(this::replayEvent);

          if (batchSourceEventPosition > snapshotPosition) {
            lastProcessedPositionState.markAsProcessed(batchSourceEventPosition);
          }
        });

    zeebeDbTransaction.commit();
    zeebeDbTransaction = null;

    return true;
  }

  private void replayEvent(final LoggedEvent currentEvent) {
    if (eventFilter.applies(currentEvent)
        && (currentEvent.getSourceEventPosition() > snapshotPosition
            || currentEvent.getSourceEventPosition()
                < 0)) { // some events might not have a source pointer
      readMetadata(currentEvent);
      final var currentTypedEvent = readRecordValue(currentEvent);
      LOG.trace("Replaying event {}: {}", currentTypedEvent.getPosition(), currentTypedEvent);
      currentStateDescription = () -> "replaying event %s".formatted(typedEvent);

      final var processor =
          recordProcessors.stream()
              .filter(p -> p.accepts(currentTypedEvent.getValueType()))
              .findFirst()
              .orElseThrow(() -> NoSuchProcessorException.forRecord(currentTypedEvent));

      processor.replay(currentTypedEvent);
      lastReplayedEventPosition = currentTypedEvent.getPosition();
    }

    onRecordReplayed(currentEvent);
  }

  /**
   * Ends the replay and sets some important properties, especially completes the replay future with
   * the last processing positions.
   */
  private void onRecordsReplayed() {
    // Each event is caused by a command, the event points to the source command via the
    // source position (pointer). This means the last source position is equal to the last processed
    // position by the processing state machine, which we can backfill after replay.
    final var lastProcessedPosition = lastSourceEventPosition;
    currentStateDescription = () -> "replay finished";

    // The replay state machine reads all records, but only applies the events.
    // The last read record position can be used as lastWrittenPosition in order to
    // init the processing state machine.
    final var lastWrittenPosition = lastReadRecordPosition;
    final var lastProcessingPositions =
        new LastProcessingPositions(lastProcessedPosition, lastWrittenPosition);

    LOG.info(LOG_STMT_REPLAY_FINISHED, lastProcessingPositions);
    recoveryFuture.complete(lastProcessingPositions);
  }

  /**
   * Stages meta data details of the current applied event. The stored properties are later used
   * after the replay is done.
   *
   * <p>It will schedule the next replay iteration.
   */
  private void onRecordReplayed(final LoggedEvent currentEvent) {
    replayMetrics.event();
    // release the reference to the event as soon as possible
    final var sourceEventPosition = currentEvent.getSourceEventPosition();
    final var currentPosition = currentEvent.getPosition();
    final var currentRecordKey = currentEvent.getKey();

    // positions should always increase
    // if this is not the case we have some inconsistency in our log
    if (lastReadRecordPosition >= currentPosition) {
      throw new IllegalStateException(
          String.format(ERROR_INCONSISTENT_LOG, currentPosition, lastReadRecordPosition));
    }
    lastReadRecordPosition = currentPosition;

    // we need to keep track of the last source event position to know where to start with
    // processing after replay
    batchSourceEventPosition = sourceEventPosition;

    // records from other partitions should not influence the key generator of this partition
    if (Protocol.decodePartitionId(currentRecordKey) == partitionId) {
      keyGeneratorControls.setKeyIfHigher(currentRecordKey);
    }
  }

  /**
   * Reads the metadata of the current read event.
   *
   * @throws ProcessingException if an error occurs during reading the metadata
   */
  private void readMetadata(final LoggedEvent currentEvent) throws ProcessingException {
    try {
      metadata.reset();
      currentEvent.readMetadata(metadata);
    } catch (final Exception e) {
      final var errorMsg = String.format(ERROR_MSG_EXPECTED_TO_READ_METADATA, currentEvent);
      throw new ProcessingException(errorMsg, currentEvent, null, e);
    }
  }

  private TypedRecord<?> readRecordValue(final LoggedEvent currentEvent) {
    final UnifiedRecordValue value =
        recordValues.readRecordValue(currentEvent, metadata.getValueType());
    typedEvent.wrap(currentEvent, metadata, value);
    return typedEvent;
  }

  public long getLastSourceEventPosition() {
    return lastSourceEventPosition;
  }

  public long getLastReplayedEventPosition() {
    return lastReplayedEventPosition;
  }

  Supplier<String> describeCurrentState() {
    return currentStateDescription;
  }

  public void close() {
    logStream.removeRecordAvailableListener(this);
  }

  private enum State {
    AWAIT_RECORD,
    REPLAY_EVENT
  }
}
