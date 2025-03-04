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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultExecutionQueue implements ExecutionQueue {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultExecutionQueue.class);
  private static final Set<String> IGNORE_EMPTY_UPDATES =
      Set.of(
          "io.camunda.db.rdbms.sql.UserTaskMapper.updateHistoryCleanupDate",
          "io.camunda.db.rdbms.sql.UserTaskMapper.deleteCandidateUsers",
          "io.camunda.db.rdbms.sql.UserTaskMapper.deleteCandidateGroups",
          "io.camunda.db.rdbms.sql.IncidentMapper.updateHistoryCleanupDate",
          "io.camunda.db.rdbms.sql.DecisionInstanceMapper.updateHistoryCleanupDate",
          "io.camunda.db.rdbms.sql.VariableMapper.updateHistoryCleanupDate");

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
        LOG.trace(
            "[RDBMS ExecutionQueue, Partition {}] Skip Flushing because execution queue is empty",
            partitionId);
        return 0;
      }

      LOG.trace("[RDBMS ExecutionQueue, Partition {}] flushing queue", partitionId);
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
    final var optimizedItems = optimizeQueueOrder(queue);

    try {
      for (final var entry : optimizedItems) {
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
        if (Arrays.stream(singleBatchResult.getUpdateCounts()).anyMatch(i -> i == 0)
            && !IGNORE_EMPTY_UPDATES.contains(singleBatchResult.getMappedStatement().getId())) {
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

  /**
   * Optimizes the order of the queue items to minimize the number of executed statements. Primary
   * goal of this optimization is to batch as many statements as possible For this statements with
   * the same MyBatis-ID have to be executed sequentially directly after each other. A second goal
   * is to ensure that INSERT statements are always executed before UPDATE statements. <br>
   * The optimization happens in two steps: <br>
   * <br>
   * First the queue is grouped by the {@link ContextType}. Here the order of the items inside this
   * group is still preserved.<br>
   * <br>
   * In the second step the items inside the groups are sorted by the {@link WriteStatementType}
   * (natural order) and {@link QueueItem#statementId()}. For some entities this step will lead to
   * errors. Therefore, this second step can be deactivated in the {@link ContextType}.
   *
   * @param items queue of items
   * @return optimized queue of items
   */
  private List<QueueItem> optimizeQueueOrder(final List<QueueItem> items) {
    final Map<ContextType, List<QueueItem>> itemsByContextType =
        items.stream().collect(Collectors.groupingBy(QueueItem::contextType));

    final List<QueueItem> resultList = new ArrayList<>();
    for (final var entry : itemsByContextType.entrySet()) {
      final var contextType = entry.getKey();
      if (contextType.preserveOrder()) {
        resultList.addAll(entry.getValue());
      } else {
        final var contextItems = new ArrayList<>(entry.getValue());
        contextItems.sort(
            Comparator.comparing(QueueItem::statementType).thenComparing(QueueItem::statementId));
        resultList.addAll(contextItems);
      }
    }

    return resultList;
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
