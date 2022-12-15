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
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.io.Closeable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sequencer takes in record batches from multiple writers and writes them to the {@link
 * LogStorage}. Keeps track of record positions and returns the last assigned position to writers.
 */
final class Sequencer implements LogStreamWriter, Closeable, AppendErrorHandler, HealthMonitorable {
  private static final Logger LOG = LoggerFactory.getLogger(Sequencer.class);
  private final int partitionId;
  private final int maxFragmentSize;

  private volatile long position;
  private volatile boolean isClosed = false;
  private final ReentrantLock lock = new ReentrantLock();
  private final LogStorage logStorage;
  private final AppenderFlowControl flowControl;
  private HealthReport report = HealthReport.healthy(this);
  private final Set<FailureListener> failureListeners = new HashSet<>();

  Sequencer(
      final int partitionId,
      final long initialPosition,
      final int maxFragmentSize,
      final LogStorage logStorage) {
    this.logStorage = logStorage;
    LOG.trace("Starting new sequencer at position {}", initialPosition);
    this.position = initialPosition;
    this.partitionId = partitionId;
    this.maxFragmentSize = maxFragmentSize;
    flowControl = new AppenderFlowControl(this, partitionId);
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
  public long tryWrite(final List<LogAppendEntry> appendEntries, final long sourcePosition) {
    if (isClosed) {
      LOG.warn("Rejecting write of {}, sequencer is closed", appendEntries);
      return -1;
    }

    for (final var entry : appendEntries) {
      if (!isEntryValid(entry)) {
        LOG.warn("Reject write of invalid entry {}", entry);
        return 0;
      }
    }
    final var batchSize = appendEntries.size();
    if (batchSize == 0) {
      return 0;
    }

    final var inFlightAppend = flowControl.tryAcquire();
    if (inFlightAppend.isEmpty()) {
      return -1;
    }

    final long lowestPosition;
    final long highestPosition;
    lock.lock();
    try {
      lowestPosition = position;
      highestPosition = position + batchSize - 1;
      inFlightAppend.get().start(highestPosition);
      final var batch =
          new SequencedBatch(
              ActorClock.currentTimeMillis(), lowestPosition, sourcePosition, appendEntries);
      logStorage.append(lowestPosition, highestPosition, batch, inFlightAppend.get());
      position = lowestPosition + batchSize;
    } finally {
      lock.unlock();
    }
    return highestPosition;
  }

  /**
   * Closes the sequencer. After closing, writes are rejected but reads are still allowed to drain
   * the queue. Closing the sequencer is not atomic so some writes may occur shortly after closing.
   */
  @Override
  public synchronized void close() {
    LOG.info("Closing sequencer");
    isClosed = true;
  }

  private boolean isEntryValid(final LogAppendEntry entry) {
    return entry.recordValue() != null
        && entry.recordValue().getLength() > 0
        && entry.recordMetadata() != null
        && entry.recordMetadata().getLength() > 0;
  }

  @Override
  public synchronized void onCommitError(final Throwable error) {
    final var report =
        HealthReport.unhealthy(this)
            .withIssue(HealthIssue.of(error, "Commit of written entry failed"));
    this.report = report;
    failureListeners.forEach(listener -> listener.onFailure(report));
    close();
  }

  @Override
  public synchronized void onWriteError(final Throwable error) {
    final var report =
        HealthReport.unhealthy(this)
            .withIssue(HealthIssue.of(error, "Write to log storage failed"));
    this.report = report;
    failureListeners.forEach(listener -> listener.onFailure(report));
    close();
  }

  @Override
  public String getName() {
    return "Sequencer-%s".formatted(partitionId);
  }

  @Override
  public synchronized HealthReport getHealthReport() {
    return report;
  }

  @Override
  public synchronized void addFailureListener(final FailureListener failureListener) {
    failureListeners.add(failureListener);
  }

  @Override
  public synchronized void removeFailureListener(final FailureListener failureListener) {
    failureListeners.remove(failureListener);
  }
}
