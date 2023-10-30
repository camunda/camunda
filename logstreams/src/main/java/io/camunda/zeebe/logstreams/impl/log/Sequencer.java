/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor.FRAME_ALIGNMENT;

import io.camunda.zeebe.logstreams.impl.flowcontrol.AppendErrorHandler;
import io.camunda.zeebe.logstreams.impl.flowcontrol.AppenderFlowControl;
import io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.ActorCondition;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.util.Either;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sequencer is a multiple-producer, single-consumer queue of {@link LogAppendEntry}. It buffers
 * a fixed amount of entries and rejects writes when the queue is full. The consumer may read at its
 * own pace by repeatedly calling {@link Sequencer#tryRead()} or register for notifications when new
 * entries are written by calling {@link Sequencer#registerConsumer(ActorCondition)}.
 *
 * <p>The sequencer assigns all entries a position and makes that position available to its
 * consumer. The sequencer does not copy or serialize entries, it only keeps a reference to them
 * until they are handed off to the consumer.
 */
final class Sequencer implements LogStreamWriter, Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(Sequencer.class);
  private final int maxFragmentSize;

  private volatile long position;
  private volatile boolean isClosed = false;
  private final ReentrantLock lock = new ReentrantLock();
  private final SequencerMetrics metrics;
  private final LogStorage logStorage;
  private final AppenderFlowControl flowControl;

  Sequencer(
      final LogStorage logStorage,
      final int partitionId,
      final long initialPosition,
      final int maxFragmentSize) {
    this.logStorage = logStorage;
    LOG.trace("Starting new sequencer at position {}", initialPosition);
    position = initialPosition;
    this.maxFragmentSize = maxFragmentSize;
    metrics = new SequencerMetrics(partitionId);
    flowControl =
        new AppenderFlowControl(
            new AppendErrorHandler() {
              @Override
              public void onCommitError(final Throwable error) {}

              @Override
              public void onWriteError(final Throwable error) {}
            },
            partitionId);
  }

  /** {@inheritDoc} */
  @Override
  public boolean canWriteEvents(final int eventCount, final int batchSize) {
    final int framedMessageLength =
        batchSize
            + eventCount * (DataFrameDescriptor.HEADER_LENGTH + FRAME_ALIGNMENT)
            + FRAME_ALIGNMENT;
    return framedMessageLength <= maxFragmentSize;
  }

  /** {@inheritDoc} */
  @Override
  public Either<WriteFailure, Long> tryWrite(
      final List<LogAppendEntry> appendEntries, final long sourcePosition) {
    if (isClosed) {
      LOG.warn("Rejecting write of {}, sequencer is closed", appendEntries);
      return Either.left(WriteFailure.CLOSED);
    }

    for (final var entry : appendEntries) {
      if (!isEntryValid(entry)) {
        LOG.warn("Reject write of invalid entry {}", entry);
        return Either.left(WriteFailure.INVALID_ARGUMENT);
      }
    }
    final var batchSize = appendEntries.size();
    if (batchSize == 0) {
      return Either.left(WriteFailure.INVALID_ARGUMENT);
    }

    final var permit = flowControl.tryAcquire();
    if (permit.isEmpty()) {
      return Either.left(WriteFailure.FULL);
    }

    final SequencedBatch sequenced;
    final long lowestPosition;
    final long highestPosition;
    lock.lock();
    try {
      lowestPosition = position;
      highestPosition = lowestPosition + batchSize - 1;
      sequenced =
          new SequencedBatch(
              ActorClock.currentTimeMillis(), lowestPosition, sourcePosition, appendEntries);
      logStorage.append(
          lowestPosition, highestPosition, sequenced, permit.get().start(highestPosition));
      position = highestPosition + 1;
    } finally {
      lock.unlock();
    }
    metrics.observeBatchLengthBytes(sequenced.getLength());
    metrics.observeBatchSize(batchSize);
    return Either.right(highestPosition);
  }

  /**
   * Closes the sequencer. After closing, writes are rejected but reads are still allowed to drain
   * the queue. Closing the sequencer is not atomic so some writes may occur shortly after closing.
   */
  @Override
  public void close() {
    LOG.info("Closing sequencer for writing");
    isClosed = true;
  }

  private boolean isEntryValid(final LogAppendEntry entry) {
    return entry.recordValue() != null
        && entry.recordValue().getLength() > 0
        && entry.recordMetadata() != null
        && entry.recordMetadata().getLength() > 0;
  }
}
