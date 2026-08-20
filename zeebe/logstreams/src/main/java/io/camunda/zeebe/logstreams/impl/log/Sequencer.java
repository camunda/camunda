/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor.FRAME_ALIGNMENT;
import static io.camunda.zeebe.logstreams.impl.serializer.SequencedBatchSerializer.calculateBatchLength;

import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection;
import io.camunda.zeebe.logstreams.impl.flowcontrol.InFlightEntry;
import io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.util.Either;
import java.io.Closeable;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sequencer takes concurrent {@link #tryWrite(WriteContext, List, long) tryWrite} calls and
 * serializes them, assigning positions to all entries. Writes that are accepted are written
 * directly to the {@link LogStorage}.
 */
final class Sequencer implements LogStreamWriter, Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(Sequencer.class);
  private final int maxFragmentSize;

  private volatile long position;
  private volatile boolean isClosed = false;
  private final ReentrantLock lock = new ReentrantLock();
  private final LogStorage logStorage;
  private final InstantSource clock;
  private final SequencerMetrics sequencerMetrics;
  private final FlowControl flowControl;

  Sequencer(
      final LogStorage logStorage,
      final long initialPosition,
      final int maxFragmentSize,
      final InstantSource clock,
      final SequencerMetrics sequencerMetrics,
      final FlowControl flowControl) {
    LOG.trace("Starting new sequencer at position {}", initialPosition);
    this.logStorage = logStorage;
    this.clock = Objects.requireNonNull(clock);
    position = initialPosition;
    this.maxFragmentSize = maxFragmentSize;
    this.sequencerMetrics =
        Objects.requireNonNull(sequencerMetrics, "must specify sequencer metrics");
    this.flowControl = flowControl;
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
  // False positive: https://github.com/checkstyle/checkstyle/issues/14891
  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  public Either<WriteFailure, Long> tryWrite(
      final WriteContext context,
      final List<LogAppendEntry> appendEntries,
      final long sourcePosition) {
    if (isClosed) {
      LOG.warn("Rejecting write of {}, sequencer is closed", appendEntries);
      return Either.left(WriteFailure.CLOSED);
    }
    if (appendEntries.isEmpty()) {
      return Either.left(WriteFailure.INVALID_ARGUMENT);
    }
    for (final var entry : appendEntries) {
      if (!isEntryValid(entry)) {
        LOG.warn("Reject write of invalid entry {}", entry);
        return Either.left(WriteFailure.INVALID_ARGUMENT);
      }
    }
    final InFlightEntry inFlightEntry;
    switch (flowControl.tryAcquire(context, appendEntries)) {
      case Either.Left<Rejection, InFlightEntry>(final var rejected) -> {
        return switch (rejected) {
          case RequestLimitExhausted -> Either.left(WriteFailure.REQUEST_LIMIT_EXHAUSTED);
          case WriteRateLimitExhausted -> Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED);
          case PartitionPaused -> Either.left(WriteFailure.PARTITION_PAUSED);
        };
      }
      case Either.Right<Rejection, InFlightEntry>(final var accepted) -> inFlightEntry = accepted;
    }

    final int batchSize = appendEntries.size();
    final int batchLength = calculateBatchLength(appendEntries);

    final long waitStart = System.nanoTime();

    final boolean lockAcquiredImmediately = lock.tryLock();
    if (!lockAcquiredImmediately) {
      lock.lock();
    }

    final long acquireTime;
    final long highestPosition;
    try {
      if (flowControl.isPaused()) {
        // Frozen after this writer passed tryAcquire but before it took the lock. Reject under the
        // lock (releasing the reservation).
        inFlightEntry.cleanup();
        return Either.left(WriteFailure.PARTITION_PAUSED);
      }
      acquireTime = System.nanoTime();
      final var currentPosition = position;
      highestPosition = currentPosition + batchSize - 1;
      final var sequencedBatch =
          new SequencedBatch(
              clock.millis(), currentPosition, sourcePosition, appendEntries, batchLength);
      final var appendListener = flowControl.registerEntry(highestPosition, inFlightEntry);
      logStorage.append(currentPosition, highestPosition, sequencedBatch, appendListener);
      position = currentPosition + batchSize;
    } finally {
      lock.unlock();
    }
    final long releaseTime = System.nanoTime();

    flowControl.onAppended(inFlightEntry);

    // All metrics are recorded outside the critical section to avoid inflating lock hold time.
    recordMetrics(
        context,
        lockAcquiredImmediately,
        batchSize,
        batchLength,
        acquireTime - waitStart,
        releaseTime - acquireTime);
    return Either.right(highestPosition);
  }

  private void recordMetrics(
      final WriteContext context,
      final boolean lockAcquiredImmediately,
      final int batchSize,
      final int batchLength,
      final long waitNanos,
      final long holdNanos) {
    if (!lockAcquiredImmediately) {
      sequencerMetrics.observeLockWaitTime(context, waitNanos);
    }
    sequencerMetrics.observeLockHoldTime(context, holdNanos);
    sequencerMetrics.observeBatchLengthBytes(batchLength);
    sequencerMetrics.observeBatchSize(batchSize);
  }

  /**
   * Freezes write admission and drains in-flight writers for a leadership transfer. Taking the lock
   * ensures any writer inside the critical section has submitted its append, and any writer past
   * {@link FlowControl#tryAcquire} but still awaiting the lock is rejected once it takes the lock
   * in {@link #tryWrite}. After this returns nothing can be appended until {@link #resumeWrites()}.
   */
  void pauseWrites() {
    lock.lock();
    try {
      flowControl.pause();
    } finally {
      lock.unlock();
    }
  }

  /** Resumes write admission after a leadership transfer. */
  void resumeWrites() {
    flowControl.resume();
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

  private static boolean isEntryValid(final LogAppendEntry entry) {
    return entry.recordValue() != null && entry.recordMetadata() != null;
  }
}
