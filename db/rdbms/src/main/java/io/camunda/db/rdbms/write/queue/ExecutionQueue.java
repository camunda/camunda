/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionQueue {

  private static final Logger LOG = LoggerFactory.getLogger(ExecutionQueue.class);

  private final SqlSessionFactory sessionFactory;
  private final List<PreFlushListener> preFlushListeners = new ArrayList<>();
  private final List<PostFlushListener> postFlushListeners = new ArrayList<>();

  private final LinkedList<QueueItem> queue = new LinkedList<>();

  private final long partitionId; // for addressing the logger
  private final Integer queueFlushLimit;

  public ExecutionQueue(
      final SqlSessionFactory sessionFactory,
      final long partitionId,
      final Integer queueFlushLimit) {
    this.sessionFactory = sessionFactory;
    this.partitionId = partitionId;
    this.queueFlushLimit = queueFlushLimit;
  }

  public void executeInQueue(final QueueItem entry) {
    LOG.debug("[RDBMS ExecutionQueue, Partition {}] Added entry to queue: {}", partitionId, entry);
    synchronized (queue) {
      queue.add(entry);
      checkQueueForFlush();
    }
  }

  public void registerPreFlushListener(final PreFlushListener listener) {
    preFlushListeners.add(listener);
  }

  public void registerPostFlushListener(final PostFlushListener listener) {
    postFlushListeners.add(listener);
  }

  /**
   * Performs flush on the queue.
   *
   * @return number of flushed items
   */
  public int flush() {
    synchronized (queue) {
      if (queue.isEmpty()) {
        LOG.trace(
            "[RDBMS ExecutionQueue, Partition {}] Skip Flushing because execution queue is empty",
            partitionId);
        return 0;
      }
      LOG.debug(
          "[RDBMS ExecutionQueue, Partition {}] Flushing execution queue with {} items",
          partitionId,
          queue.size());

      final var startMillis = System.currentTimeMillis();
      final var session =
          sessionFactory.openSession(
              ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED);

      var flushedElements = 0;
      try {
        while (!queue.isEmpty()) {
          final var entry = queue.peek();
          LOG.trace("[RDBMS ExecutionQueue, Partition {}] Executing entry: {}", partitionId, entry);
          session.update(entry.statementId(), entry.parameter());
          queue.remove();
          flushedElements++;
        }

        if (!preFlushListeners.isEmpty()) {
          LOG.debug("[RDBMS ExecutionQueue, Partition {}] Call pre flush listeners", partitionId);
          preFlushListeners.forEach(PreFlushListener::onPreFlush);
        }

        session.flushStatements();
        session.commit();
        if (!postFlushListeners.isEmpty()) {
          LOG.debug("[RDBMS ExecutionQueue, Partition {}] Call post flush listeners", partitionId);
          postFlushListeners.forEach(PostFlushListener::onPostFlush);
        }
        LOG.debug(
            "[RDBMS ExecutionQueue, Partition {}] Commit queue with {} entries in {}ms",
            partitionId,
            flushedElements,
            System.currentTimeMillis() - startMillis);

        return flushedElements;
      } catch (final Exception e) {
        LOG.error(
            "[RDBMS ExecutionQueue, Partition {}] Error while executing queue", partitionId, e);
        session.rollback();

        throw e;
      } finally {
        session.close();
      }
    }
  }

  /**
   * Iterate from end over the queue and try to find a last added compatible queueItem. The
   * queueItem will be replaced with a new, combined queueItem.
   */
  public boolean tryMergeWithExistingQueueItem(final QueueItemMerger... combiners) {
    synchronized (queue) {
      int index = queue.size() - 1;
      for (final Iterator<QueueItem> it = queue.descendingIterator(); it.hasNext(); ) {
        final QueueItem item = it.next();

        for (final QueueItemMerger merger : combiners) {
          if (merger.canBeMerged(item)) {
            LOG.debug("Merging new item with item {}, {}", item.contextType(), item.id());
            queue.set(index, merger.merge(item));
            return true;
          }
        }

        index--;
      }

      return false;
    }
  }

  LinkedList<QueueItem> getQueue() {
    return queue;
  }

  private void checkQueueForFlush() {
    LOG.trace(
        "[RDBMS ExecutionQueue, Partition {}] Checking if queue is flushed. Queue size: {}",
        partitionId,
        queue.size());
    if (queue.size() >= queueFlushLimit) {
      flush();
    }
  }
}
