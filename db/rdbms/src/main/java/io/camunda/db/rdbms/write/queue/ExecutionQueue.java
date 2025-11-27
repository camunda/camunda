/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

public interface ExecutionQueue {

  /**
   * Sets the current record timestamp context. All subsequent calls to {@link
   * #executeInQueue(QueueItem)} will use this timestamp until it is cleared or changed. This is
   * used to track the record creation time for calculating exporting latency.
   *
   * @param timestampMs the record creation timestamp in milliseconds, or -1 to clear the context
   */
  void setCurrentRecordTimestamp(long timestampMs);

  /**
   * Enqueues the given entry to be executed later in a batch.
   *
   * @param entry the queue item to enqueue
   */
  void executeInQueue(QueueItem entry);

  /**
   * Registers a listener to be called before flushing the queue.<br>
   * <br>
   * Note: When multiple listeners are registered, they are called in the order of their
   * registration.
   *
   * @param listener the pre-flush listener to register
   */
  void registerPreFlushListener(PreFlushListener listener);

  /**
   * Registers a listener to be called right after flushing the queue.<br>
   * <br>
   * Note: When multiple listeners are registered, they are called in the order of their
   * registration.
   *
   * @param listener the post-flush listener to register
   */
  void registerPostFlushListener(PostFlushListener listener);

  /**
   * Flushes the queue, executing all enqueued items in a batch.
   *
   * @return the number of flushed items
   */
  int flush();

  /**
   * Takes the given queueItemMerger and processes all queue items with it. The queueItemMerger will
   * try to find a matching queue item and eventually then modify this queue item.
   *
   * @param merger the queue item merger to apply
   * @return if a merge has been performed
   */
  boolean tryMergeWithExistingQueueItem(QueueItemMerger merger);

  /**
   * Checks if the queue has reached its flush limit and flushes if necessary. Does nothing if no
   * flush limit is configured (both queueFlushLimit <= 0 and queueMemoryLimit <= 0).
   *
   * <p>The queue will be flushed if either the count-based limit or the memory-based limit is
   * exceeded.
   *
   * @return true if the queue was flushed, false otherwise
   */
  boolean checkQueueForFlush();
}
