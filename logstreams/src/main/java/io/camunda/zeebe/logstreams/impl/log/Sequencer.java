/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor.FRAME_ALIGNMENT;

import io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.scheduler.ActorCondition;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
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
final class Sequencer implements LogStreamBatchWriter, Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(Sequencer.class);
  private final int partitionId;
  private final int maxFragmentSize;

  private volatile long position;
  private volatile boolean isClosed = false;
  private volatile ActorCondition consumer;
  private final Queue<SequencedBatch> queue = new ArrayBlockingQueue<>(128);
  private final ReentrantLock lock = new ReentrantLock();
  private final SequencerMetrics metrics;

  Sequencer(final int partitionId, final long initialPosition, final int maxFragmentSize) {
    LOG.trace("Starting new sequencer at position {}", initialPosition);
    this.position = initialPosition;
    this.partitionId = partitionId;
    this.maxFragmentSize = maxFragmentSize;
    this.metrics = new SequencerMetrics(partitionId);
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
  public long tryWrite(final LogAppendEntry appendEntry, final long sourcePosition) {
    if (isClosed) {
      LOG.warn("Rejecting write of {}, sequencer is closed", appendEntry);
      return -1;
    }

    if (!isEntryValid(appendEntry)) {
      LOG.warn("Reject write of invalid entry {}", appendEntry);
      return 0;
    }

    final boolean isEnqueued;
    final long currentPosition;

    lock.lock();
    try {
      currentPosition = position;
      isEnqueued =
          queue.offer(new SequencedBatch(currentPosition, sourcePosition, List.of(appendEntry)));
      if (isEnqueued) {
        position = currentPosition + 1;
      }
    } finally {
      lock.unlock();
    }

    if (consumer != null) {
      consumer.signal();
    }
    metrics.observeBatchSize(1);
    metrics.setQueueSize(queue.size());
    if (isEnqueued) {
      return currentPosition;
    } else {
      LOG.trace("Rejecting write of {}, sequencer queue is full", appendEntry);
      return -1;
    }
  }

  /** {@inheritDoc} */
  @Override
  public long tryWrite(
      final Iterable<? extends LogAppendEntry> appendEntries, final long sourcePosition) {
    if (isClosed) {
      LOG.warn("Rejecting write of {}, sequencer is closed", appendEntries);
      return -1;
    }

    final var entries = new ArrayList<LogAppendEntry>(16);
    for (final var entry : appendEntries) {
      if (!isEntryValid(entry)) {
        LOG.warn("Reject write of invalid entry {}", entry);
        return 0;
      }
      entries.add(entry);
    }
    final var batchSize = entries.size();
    if (batchSize == 0) {
      return 0;
    }

    final long currentPosition;
    final boolean isEnqueued;
    lock.lock();
    try {
      currentPosition = position;
      isEnqueued = queue.offer(new SequencedBatch(currentPosition, sourcePosition, entries));
      if (isEnqueued) {
        position = currentPosition + batchSize;
      }
    } finally {
      lock.unlock();
    }

    if (consumer != null) {
      consumer.signal();
    }
    metrics.setQueueSize(queue.size());
    if (isEnqueued) {
      metrics.observeBatchSize(batchSize);
      return currentPosition + batchSize - 1;
    } else {
      LOG.trace("Rejecting write of {}, sequencer queue is full", entries);
      return -1;
    }
  }

  /**
   * Retrieves, but does not remove, the first item in the sequenced batch queue.
   *
   * @return A {@link SequencedBatch} or null if none is available
   */
  SequencedBatch tryRead() {
    return queue.poll();
  }

  SequencedBatch peek() {
    return queue.peek();
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

  void registerConsumer(final ActorCondition consumer) {
    this.consumer = consumer;
  }

  private boolean isEntryValid(final LogAppendEntry entry) {
    return entry.recordValue() != null
        && entry.recordValue().getLength() > 0
        && entry.recordMetadata() != null; // metadata is currently allowed to be empty;
  }
}
