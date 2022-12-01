/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.scheduler.ActorCondition;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

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
public final class Sequencer implements LogStreamBatchWriter, Closeable {
  private volatile long position;
  private volatile boolean isClosed = false;
  private ActorCondition consumer;
  private final Queue<SequencedBatch> queue = new ArrayBlockingQueue<>(128);
  private final ReentrantLock lock = new ReentrantLock();

  public Sequencer(final long initialPosition) {
    this.position = initialPosition;
  }

  /**
   * @param eventCount the potential event count we want to check
   * @param batchSize the potential batch Size (in bytes) we want to check
   * @return Always returns true, regardless of the batch size.
   */
  @Override
  public boolean canWriteEvents(final int eventCount, final int batchSize) {
    return true;
  }

  /**
   * @param appendEntry the entry to write
   * @param sourcePosition a back-pointer to the record whose processing created this entry
   * @return -1 if write was rejected, the position of the entry if write was successful.
   */
  @Override
  public long tryWrite(final LogAppendEntry appendEntry, final long sourcePosition) {
    if (isClosed) {
      return -1;
    }
    lock.lock();
    try {
      final var currentPosition = position;
      final var isEnqueued =
          queue.offer(new SequencedBatch(currentPosition, sourcePosition, List.of(appendEntry)));
      if (isEnqueued) {
        position = currentPosition + 1;
        if (consumer != null) {
          consumer.signal();
        }
        return currentPosition;
      } else {
        return -1;
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * @param appendEntries a set of entries to append; these will be appended in the order in which
   *     the collection is iterated.
   * @param sourcePosition a back-pointer to the record whose processing created these entries
   * @return -1 if write was rejected, 0 if batch was empty, the highest position of the batch if
   *     write was successful.
   */
  @Override
  public long tryWrite(
      final Iterable<? extends LogAppendEntry> appendEntries, final long sourcePosition) {
    if (isClosed) {
      return -1;
    }

    final var entries = new ArrayList<LogAppendEntry>();
    for (final var entry : appendEntries) {
      entries.add(entry);
    }
    final var batchSize = entries.size();
    if (batchSize == 0) {
      return 0;
    }

    lock.lock();
    try {
      final var firstPosition = position;
      final var isEnqueued =
          queue.offer(new SequencedBatch(firstPosition, sourcePosition, entries));
      if (isEnqueued) {
        final var nextPosition = firstPosition + batchSize;
        position = nextPosition;
        if (consumer != null) {
          consumer.signal();
        }
        return nextPosition - 1;
      } else {
        return -1;
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Tries to read a {@link SequencedBatch} from the queue.
   *
   * @return A {@link SequencedBatch} or null if none is available.
   */
  public SequencedBatch tryRead() {
    return queue.poll();
  }

  public SequencedBatch peek() {
    return queue.peek();
  }

  /**
   * Closes the sequencer. After closing, writes are rejected but reads are still allowed to drain
   * the queue. Closing the sequencer is not atomic so some writes may occur shortly after closing.
   */
  @Override
  public void close() {
    isClosed = true;
  }

  /**
   * @return true if the sequencer is closed for writing.
   */
  public boolean isClosed() {
    return isClosed;
  }

  public void registerConsumer(final ActorCondition consumer) {
    this.consumer = consumer;
  }

  record SequencedBatch(long firstPosition, long sourcePosition, List<LogAppendEntry> entries) {}
}
