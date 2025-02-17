/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultExecutionQueue implements ExecutionQueue {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultExecutionQueue.class);

  private final SqlSessionFactory sessionFactory;
  private final List<PreFlushListener> preFlushListeners = new ArrayList<>();
  private final List<PostFlushListener> postFlushListeners = new ArrayList<>();

  private final LinkedList<QueueItem> queue = new LinkedList<>();

  private final long partitionId; // for addressing the logger
  private final int queueFlushLimit;

  private final RdbmsWriterMetrics metrics;

  public DefaultExecutionQueue(
      final SqlSessionFactory sessionFactory,
      final long partitionId,
      final int queueFlushLimit,
      final RdbmsWriterMetrics metrics) {
    this.sessionFactory = sessionFactory;
    this.partitionId = partitionId;
    this.queueFlushLimit = queueFlushLimit;
    this.metrics = metrics;
  }

  @Override
  public void executeInQueue(final QueueItem entry) {
    LOG.trace("[RDBMS ExecutionQueue, Partition {}] Added entry to queue: {}", partitionId, entry);
    synchronized (queue) {
      if (queue.isEmpty()) {
        metrics.startFlushLatencyMeasurement();
      }

      queue.add(entry);
      metrics.recordEnqueuedStatement(entry.statementId());
      checkQueueForFlush();
    }
  }

  @Override
  public void registerPreFlushListener(final PreFlushListener listener) {
    preFlushListeners.add(listener);
  }

  @Override
  public void registerPostFlushListener(final PostFlushListener listener) {
    postFlushListeners.add(listener);
  }

  /**
   * Performs flush on the queue.
   *
   * @return number of flushed items
   */
  @Override
  public int flush() {
    synchronized (queue) {
      if (queue.isEmpty()) {
        LOG.debug(
            "[RDBMS ExecutionQueue, Partition {}] Skip Flushing because execution queue is empty",
            partitionId);
        return 0;
      }
      try (final var ignored = metrics.measureFlushDuration()) {
        final int numFlushedElements = doFLush();
        metrics.stopFlushLatencyMeasurement();
        metrics.recordBulkSize(numFlushedElements);

        return numFlushedElements;
      } catch (final Exception e) {
        metrics.recordFailedFlush();
        throw e;
      }
    }
  }

  /**
   * Iterate from end over the queue and try to find a last added compatible queueItem. The
   * queueItem will be replaced with a new, combined queueItem.
   */
  @Override
  public boolean tryMergeWithExistingQueueItem(final QueueItemMerger... combiners) {
    synchronized (queue) {
      int index = queue.size() - 1;
      for (final Iterator<QueueItem> it = queue.descendingIterator(); it.hasNext(); ) {
        final QueueItem item = it.next();

        for (final QueueItemMerger merger : combiners) {
          if (merger.canBeMerged(item)) {
            LOG.trace("Merging new item with item {}, {}", item.contextType(), item.id());
            queue.set(index, merger.merge(item));
            metrics.recordMergedQueueItem(item.contextType(), item.statementId());
            return true;
          }
        }

        index--;
      }

      return false;
    }
  }

  private int doFLush() {
    LOG.debug(
        "[RDBMS ExecutionQueue, Partition {}] Flushing execution queue with {} items",
        partitionId,
        queue.size());

    final var startMillis = System.currentTimeMillis();
    final var session =
        sessionFactory.openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED);

    var flushedElements = 0;
    final var items = new ArrayList<>(queue);
    items.sort(Comparator.comparing(QueueItem::contextType).thenComparing(QueueItem::statementId));

    try {
      for (final var entry : items) {
        LOG.trace("[RDBMS ExecutionQueue, Partition {}] Executing entry: {}", partitionId, entry);
        session.update(entry.statementId(), entry.parameter());
        queue.remove();
        flushedElements++;
      }

      if (!preFlushListeners.isEmpty()) {
        LOG.trace("[RDBMS ExecutionQueue, Partition {}] Call pre flush listeners", partitionId);
        preFlushListeners.forEach(PreFlushListener::onPreFlush);
      }

      final var batchResult = session.flushStatements();
      for (final BatchResult singleBatchResult : batchResult) {
        if (Arrays.stream(singleBatchResult.getUpdateCounts()).anyMatch(i -> i == 0)) {
          LOG.error(
              "[RDBMS ExecutionQueue, Partition {}] Some statements with ID {} were not executed successfully",
              partitionId,
              singleBatchResult.getMappedStatement().getId());
        }
        metrics.recordExecutedStatement(
            singleBatchResult.getMappedStatement().getId(),
            singleBatchResult.getParameterObjects().size());
      }

      session.commit();
      if (!postFlushListeners.isEmpty()) {
        LOG.trace("[RDBMS ExecutionQueue, Partition {}] Call post flush listeners", partitionId);
        postFlushListeners.forEach(PostFlushListener::onPostFlush);
      }
      LOG.debug(
          "[RDBMS ExecutionQueue, Partition {}] Commit queue with {} entries in {}ms",
          partitionId,
          flushedElements,
          System.currentTimeMillis() - startMillis);

      return flushedElements;
    } catch (final Exception e) {
      LOG.error("[RDBMS ExecutionQueue, Partition {}] Error while executing queue", partitionId, e);
      session.rollback();

      throw e;
    } finally {
      session.close();
    }
  }

  LinkedList<QueueItem> getQueue() {
    return queue;
  }

  private void checkQueueForFlush() {
    if (queueFlushLimit <= 0) {
      // no limits, exporter must take care of it
      return;
    }

    LOG.trace(
        "[RDBMS ExecutionQueue, Partition {}] Checking if queue is flushed. Queue size: {}",
        partitionId,
        queue.size());
    if (queue.size() >= queueFlushLimit) {
      flush();
    }
  }
}
