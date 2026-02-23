/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics.FlushTrigger;
import io.camunda.db.rdbms.write.util.PerMessageThrottledLogger;
import io.camunda.zeebe.util.ObjectSizeEstimator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultExecutionQueue implements ExecutionQueue {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultExecutionQueue.class);
  private static final PerMessageThrottledLogger THROTTLED_LOG =
      new PerMessageThrottledLogger(LOG, Duration.ofMinutes(5));
  private static final long BYTES_PER_MB = 1024L * 1024L;
  private static final Set<Pattern> IGNORE_EMPTY_UPDATES =
      Set.of(
          Pattern.compile(".*update.*HistoryCleanupDate$"),
          Pattern.compile("io.camunda.db.rdbms.sql.SequenceFlowMapper.createIfNotExists"));

  private final SqlSessionFactory sessionFactory;
  private final List<PreFlushListener> preFlushListeners = new ArrayList<>();
  private final List<PostFlushListener> postFlushListeners = new ArrayList<>();

  private final LinkedList<QueueItem> queue = new LinkedList<>();

  private final long partitionId; // for addressing the logger
  private final int queueFlushLimit;
  private final long queueMemoryLimitBytes; // stored as bytes for comparison

  private final RdbmsWriterMetrics metrics;

  // Track current memory consumption of the queue
  private long currentQueueMemoryBytes = 0;

  public DefaultExecutionQueue(
      final SqlSessionFactory sessionFactory,
      final long partitionId,
      final int queueFlushLimit,
      final int queueMemoryLimitMb,
      final RdbmsWriterMetrics metrics) {
    this.sessionFactory = sessionFactory;
    this.partitionId = partitionId;
    this.queueFlushLimit = queueFlushLimit;
    // Convert MB to bytes for internal comparison
    queueMemoryLimitBytes = (long) queueMemoryLimitMb * BYTES_PER_MB;
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
      // Track memory consumption
      final long entrySize = ObjectSizeEstimator.estimateSize(entry);
      currentQueueMemoryBytes += entrySize;

      metrics.recordEnqueuedStatement(entry.statementId());
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
        // Record memory usage before flush
        metrics.recordQueueMemoryUsage(currentQueueMemoryBytes);
        final int numFlushedElements = doFLush();
        metrics.stopFlushLatencyMeasurement();
        metrics.recordBulkSize(numFlushedElements);
        // Reset memory tracking after flush
        currentQueueMemoryBytes = 0;

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
  public boolean tryMergeWithExistingQueueItem(final QueueItemMerger merger) {
    synchronized (queue) {
      int index = queue.size() - 1;
      for (final Iterator<QueueItem> it = queue.descendingIterator(); it.hasNext(); ) {
        final QueueItem item = it.next();

        if (merger.canBeMerged(item)) {
          LOG.trace("Merging new item with item {}, {}", item.contextType(), item.id());
          final QueueItem oldItem = item;
          final long oldSize = ObjectSizeEstimator.estimateSize(oldItem);
          final QueueItem newItem = merger.merge(oldItem);
          final long newSize = ObjectSizeEstimator.estimateSize(newItem);
          queue.set(index, newItem);
          currentQueueMemoryBytes = currentQueueMemoryBytes - oldSize + newSize;
          metrics.recordMergedQueueItem(item.contextType(), item.statementId());
          return true;
        }

        index--;
      }

      return false;
    }
  }

  @Override
  public boolean checkQueueForFlush() {
    final boolean hasCountLimit = queueFlushLimit > 0;
    final boolean hasMemoryLimit = queueMemoryLimitBytes > 0;

    if (!hasCountLimit && !hasMemoryLimit) {
      // no limits, exporter must take care of it
      return false;
    }

    final int queueSize = queue.size();
    final long queueMemory = currentQueueMemoryBytes;

    LOG.trace(
        "[RDBMS ExecutionQueue, Partition {}] Checking if queue is flushed. Queue size: {}, memory: {} bytes",
        partitionId,
        queueSize,
        queueMemory);

    // Flush if either limit is exceeded
    final boolean countLimitExceeded = hasCountLimit && queueSize >= queueFlushLimit;
    final boolean memoryLimitExceeded = hasMemoryLimit && queueMemory >= queueMemoryLimitBytes;

    if (countLimitExceeded || memoryLimitExceeded) {
      if (countLimitExceeded) {
        LOG.debug(
            "[RDBMS ExecutionQueue, Partition {}] Queue count limit exceeded: {} >= {}",
            partitionId,
            queueSize,
            queueFlushLimit);
        metrics.recordQueueFlush(FlushTrigger.COUNT_LIMIT);
      }
      if (memoryLimitExceeded) {
        LOG.debug(
            "[RDBMS ExecutionQueue, Partition {}] Queue memory limit exceeded: {} >= {} bytes",
            partitionId,
            queueMemory,
            queueMemoryLimitBytes);
        metrics.recordQueueFlush(FlushTrigger.MEMORY_LIMIT);
      }
      flush();
      return true;
    }

    return false;
  }

  private int doFLush() {
    LOG.debug(
        "[RDBMS ExecutionQueue, Partition {}] Flushing execution queue with {} items",
        partitionId,
        queue.size());

    final var startMillis = System.currentTimeMillis();

    if (!preFlushListeners.isEmpty()) {
      LOG.trace("[RDBMS ExecutionQueue, Partition {}] Call pre flush listeners", partitionId);
      preFlushListeners.forEach(PreFlushListener::onPreFlush);
    }

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

      final var batchResult = session.flushStatements();
      for (final BatchResult singleBatchResult : batchResult) {
        if (Arrays.stream(singleBatchResult.getUpdateCounts()).anyMatch(i -> i == 0)
            && !shouldIgnoreWhenNoRowsAffected(singleBatchResult.getMappedStatement().getId())) {
          THROTTLED_LOG.warn(
              "[RDBMS ExecutionQueue, Partition {}] Some statements with ID {} have not affected any rows. Either add them to the ignore list or check why no rows were affected.",
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

  static boolean shouldIgnoreWhenNoRowsAffected(final String statementId) {
    return IGNORE_EMPTY_UPDATES.stream().anyMatch(p -> p.matcher(statementId).matches());
  }
}
