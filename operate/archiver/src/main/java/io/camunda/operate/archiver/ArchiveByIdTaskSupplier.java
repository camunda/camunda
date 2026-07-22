/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import io.camunda.operate.Metrics;
import io.camunda.operate.property.ArchiverProperties;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.elasticsearch.ElasticsearchException;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;

public class ArchiveByIdTaskSupplier<T> {

  private static final int MINIMUM_BATCH_SIZE = 50;
  private static final double BATCH_SIZE_REDUCTION_FACTOR = 0.5;
  private static final List<Class<? extends Throwable>> RETRYABLE_EXCEPTIONS =
      List.of(
          SocketTimeoutException.class,
          ElasticsearchException.class,
          OpenSearchException.class,
          BatchCountMismatchException.class);

  private final String sourceIdx;
  private final String destinationIdx;
  private final BiFunction<List<T>, Integer, CompletableFuture<ArchiveDocIdsBatch<T>>> idsSupplier;
  private final Reindexer reindexer;
  private final Deleter deleter;
  private final Executor executor;
  private final ArchiverProperties archiverProperties;
  private final Metrics metrics;
  private final Logger logger;

  private final AtomicReference<ArchiveDocIdsBatch<T>> lastSearchResponse =
      new AtomicReference<>(null);
  private final AtomicBoolean finished = new AtomicBoolean(false);
  private final AtomicInteger retryCount = new AtomicInteger(0);
  private final AtomicInteger batchSize;
  private final AtomicLong totalArchived = new AtomicLong(0);
  private final AtomicLong totalTimeTakenMs = new AtomicLong(0);

  public ArchiveByIdTaskSupplier(
      final String sourceIdx,
      final String destinationIdx,
      final BiFunction<List<T>, Integer, CompletableFuture<ArchiveDocIdsBatch<T>>> idsSupplier,
      final Reindexer reindexer,
      final Deleter deleter,
      final Executor executor,
      final ArchiverProperties archiverProperties,
      final Metrics metrics,
      final Logger logger) {
    this.sourceIdx = sourceIdx;
    this.destinationIdx = destinationIdx;
    this.idsSupplier = idsSupplier;
    this.reindexer = reindexer;
    this.deleter = deleter;
    this.executor = executor;
    this.archiverProperties = archiverProperties;
    this.metrics = metrics;
    this.logger = logger;
    batchSize = new AtomicInteger(archiverProperties.getArchiveByIdBatchSize());
  }

  public boolean isComplete() {
    return finished.get();
  }

  public long getTotalArchived() {
    return totalArchived.get();
  }

  public long getTotalTimeTakenMs() {
    return totalTimeTakenMs.get();
  }

  public CompletableFuture<Long> moveNextBatch() {
    final long startMs = System.currentTimeMillis();
    return idsSupplier
        .apply(getLastSearchPosition(), batchSize.get())
        .thenComposeAsync(
            batch -> {
              if (batch.isEmpty()) {
                finished.set(true);
                return CompletableFuture.completedFuture(0L);
              }
              return reindex(batch)
                  .thenCompose(reindexedCount -> delete(batch))
                  .thenApply(
                      deletedCount -> {
                        lastSearchResponse.set(batch);
                        totalArchived.accumulateAndGet(deletedCount, Long::sum);
                        retryCount.set(0);
                        return deletedCount;
                      })
                  .exceptionallyCompose(
                      ex -> {
                        if (isRetryableError(ex)
                            && retryCount.incrementAndGet()
                                <= archiverProperties.getArchiveByIdMaxRetryAttempts()) {
                          metrics.recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);
                          adjustBatchSize(ex);

                          logger.trace(
                              "Encountered retryable error when archiving docs from '{}' to '{}', "
                                  + "retrying the batch (attempt {}/{}). Next batch size {}. Error: {}",
                              sourceIdx,
                              destinationIdx,
                              retryCount.get(),
                              archiverProperties.getArchiveByIdMaxRetryAttempts(),
                              batchSize.get(),
                              ex.getMessage());
                          final int delayMs =
                              archiverProperties.getArchiveByIdRetryDelayMs() * retryCount.get();
                          return CompletableFuture.supplyAsync(
                              () -> 0L,
                              CompletableFuture.delayedExecutor(
                                  delayMs, TimeUnit.MILLISECONDS, executor));
                        }
                        retryCount.set(0);
                        throw ex instanceof final RuntimeException re
                            ? re
                            : new RuntimeException(ex);
                      });
            },
            executor)
        .whenCompleteAsync(
            (val, err) ->
                totalTimeTakenMs.accumulateAndGet(System.currentTimeMillis() - startMs, Long::sum),
            executor);
  }

  private List<T> getLastSearchPosition() {
    final ArchiveDocIdsBatch<T> last = lastSearchResponse.get();
    return last == null ? List.of() : last.searchAfter();
  }

  private void adjustBatchSize(final Throwable ex) {
    if (batchSize.get() <= MINIMUM_BATCH_SIZE) {
      return;
    }

    if (shouldReduceBatchSize(ex)) {
      batchSize.set(
          (int) Math.max(MINIMUM_BATCH_SIZE, batchSize.get() * BATCH_SIZE_REDUCTION_FACTOR));
    }
  }

  private CompletableFuture<Long> reindex(final ArchiveDocIdsBatch<T> batch) {
    return reindexer
        .apply(sourceIdx, destinationIdx, batch.documents())
        .thenApply(
            reindexCount ->
                validateProcessedCount("reindex", reindexCount, batch.documents().size()));
  }

  private CompletableFuture<Long> delete(final ArchiveDocIdsBatch<T> batch) {
    return deleter
        .apply(sourceIdx, batch.documents())
        .thenApply(
            deleteCount -> validateProcessedCount("delete", deleteCount, batch.documents().size()));
  }

  private long validateProcessedCount(
      final String operation, final long processedCount, final int expectedCount) {
    if (processedCount < expectedCount) {
      throw new BatchCountMismatchException(
          operation,
          String.format(
              "The '%s' operation for a batch when archiving %s processed %d docs, however was "
                  + "expecting %d docs",
              operation, sourceIdx, processedCount, expectedCount));
    } else if (processedCount > expectedCount) {
      logger.warn(
          "The '{}' operation for a batch when archiving {} processed {} docs, however was expecting {} docs",
          operation,
          sourceIdx,
          processedCount,
          expectedCount);
    }
    return processedCount;
  }

  private boolean shouldReduceBatchSize(final Throwable thr) {
    return matchesThrowableOrCause(thr, SocketTimeoutException.class);
  }

  private boolean isRetryableError(final Throwable thr) {
    return RETRYABLE_EXCEPTIONS.stream().anyMatch(clazz -> matchesThrowableOrCause(thr, clazz));
  }

  private boolean matchesThrowableOrCause(
      final Throwable thr, final Class<? extends Throwable> throwableClass) {
    return thr != null
        && (throwableClass.isInstance(thr) || throwableClass.isInstance(thr.getCause()));
  }

  public record ArchiveDocIdsBatch<T>(List<IdWithRouting> documents, List<T> searchAfter) {

    static <T> ArchiveDocIdsBatch<T> empty() {
      return new ArchiveDocIdsBatch<>(List.of(), List.of());
    }

    static <T> ArchiveDocIdsBatch<T> from(
        final List<IdWithRouting> documents, final List<T> searchAfter) {
      return new ArchiveDocIdsBatch<>(documents, searchAfter);
    }

    boolean isEmpty() {
      return documents.isEmpty();
    }
  }

  public record IdWithRouting(String id, String routing) {
    public static IdWithRouting of(final String id) {
      return new IdWithRouting(id, null);
    }
  }

  /**
   * Thrown when the number of documents processed by a reindex or delete operation does not match
   * the expected count. This is caught to allow retrying the same batch on the next invocation.
   */
  static class BatchCountMismatchException extends RuntimeException {
    final String operation;

    BatchCountMismatchException(final String operation, final String message) {
      super(message);
      this.operation = operation;
    }
  }

  @FunctionalInterface
  public interface Reindexer {
    CompletableFuture<Long> apply(
        String sourceIndexName, String destinationIndexName, List<IdWithRouting> docs);
  }

  @FunctionalInterface
  public interface Deleter {
    CompletableFuture<Long> apply(String sourceIndexName, List<IdWithRouting> docs);
  }
}
