/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.google.common.base.Stopwatch;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.zeebe.util.function.TriFunction;
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
import java.util.function.Function;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;

public class ArchiveByIdTaskSupplier<SortFieldType> {

  private final HistoryConfiguration config;
  private final String sourceIdx;
  private final String destinationIdx;
  private final Function<List<SortFieldType>, CompletableFuture<ArchiveDocIdsBatch<SortFieldType>>>
      idsSupplier;
  private final TriFunction<String, String, List<String>, CompletableFuture<Long>> reindexer;
  private final BiFunction<String, List<String>, CompletableFuture<Long>> deleter;
  private final Executor executor;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;

  private final AtomicReference<ArchiveDocIdsBatch<SortFieldType>> lastSearchResponse =
      new AtomicReference<>(null);
  private final AtomicBoolean finished = new AtomicBoolean(false);
  private final AtomicInteger retryCount = new AtomicInteger(0);

  private final AtomicLong totalArchived = new AtomicLong(0);
  private final AtomicLong totalTimeTakenMs = new AtomicLong(0);

  public ArchiveByIdTaskSupplier(
      final HistoryConfiguration config,
      final String sourceIdx,
      final String destinationIdx,
      final Function<List<SortFieldType>, CompletableFuture<ArchiveDocIdsBatch<SortFieldType>>>
          idsSupplier,
      final TriFunction<String, String, List<String>, CompletableFuture<Long>> reindexer,
      final BiFunction<String, List<String>, CompletableFuture<Long>> deleter,
      final Executor executor,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    this.config = config;
    this.sourceIdx = sourceIdx;
    this.destinationIdx = destinationIdx;
    this.idsSupplier = idsSupplier;
    this.reindexer = reindexer;
    this.deleter = deleter;
    this.executor = executor;
    this.metrics = metrics;
    this.logger = logger;
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
    final Stopwatch stopwatch = Stopwatch.createStarted();
    return idsSupplier
        .apply(getLastSearchPosition())
        .thenComposeAsync(
            response -> {
              if (response.isEmpty()) {
                finished.set(true);
                return CompletableFuture.completedFuture(0L);
              }

              return reindex(response)
                  .thenCompose(reindexedCount -> delete(response))
                  .thenApply(
                      deletedCount -> {
                        // advance search position only after both archive steps completed
                        // successfully
                        lastSearchResponse.set(response);
                        totalArchived.accumulateAndGet(deletedCount, Long::sum);
                        retryCount.set(0);
                        return deletedCount;
                      })
                  .exceptionallyCompose(
                      ex -> {
                        if (isRetryableError(ex)
                            && retryCount.incrementAndGet()
                                < config.getArchiveByIdMaxRetryAttempts()) {
                          metrics.recordArchiverBatchRetry();
                          logger.trace(
                              "Encountered retryable error when archiving docs from '{}' to '{}', "
                                  + "retrying the batch (attempt {}/{}). Error: {}",
                              sourceIdx,
                              destinationIdx,
                              retryCount.get(),
                              config.getArchiveByIdMaxRetryAttempts(),
                              ex.getMessage());

                          // Whilst this is crude, we exploit the fact the ES/OS visibility is
                          // around 2 second, and incrementing delay (default=1000ms) should give a
                          // fighting chance to complete in the next attempt. If not will fail and
                          // the next retry should take this over the full 2-second refresh interval
                          final int retryDelayMs =
                              config.getArchiveByIdRetryDelayMs() * retryCount.get();
                          return CompletableFuture.supplyAsync(
                              () -> 0L,
                              CompletableFuture.delayedExecutor(
                                  retryDelayMs, TimeUnit.MILLISECONDS, executor));
                        }
                        // reset retry count so the next batch starts with fresh retries
                        retryCount.set(0);
                        // re-throw unexpected exceptions
                        throw ex instanceof final RuntimeException re
                            ? re
                            : new RuntimeException(ex);
                      });
            },
            executor)
        .whenCompleteAsync(
            (val, err) ->
                totalTimeTakenMs.accumulateAndGet(
                    stopwatch.stop().elapsed(TimeUnit.MILLISECONDS), Long::sum),
            executor);
  }

  private List<SortFieldType> getLastSearchPosition() {
    final ArchiveDocIdsBatch<SortFieldType> lstResponse = lastSearchResponse.get();
    return lstResponse == null ? List.of() : lstResponse.searchAfter();
  }

  private CompletableFuture<Long> reindex(final ArchiveDocIdsBatch<SortFieldType> response) {
    return reindexer
        .apply(sourceIdx, destinationIdx, response.ids())
        .thenApply(
            reindexCount -> validateProcessedCount("reindex", reindexCount, response.ids().size()));
  }

  private CompletableFuture<Long> delete(final ArchiveDocIdsBatch<SortFieldType> response) {
    return deleter
        .apply(sourceIdx, response.ids())
        .thenApply(
            deleteCount -> validateProcessedCount("delete", deleteCount, response.ids().size()));
  }

  private long validateProcessedCount(
      final String operation, final long processedCount, final int expectedCount) {
    if (processedCount != expectedCount) {
      throw new BatchCountMismatchException(
          operation,
          String.format(
              "The '%s' operation for a batch when archiving %s processed %d docs, however was "
                  + "expecting %d docs",
              operation, sourceIdx, processedCount, expectedCount));
    }
    return processedCount;
  }

  private boolean isRetryableError(final Throwable thr) {
    if (thr != null && thr.getCause() != null) {
      return thr.getCause() instanceof SocketTimeoutException
          || thr.getCause() instanceof ElasticsearchException
          || thr.getCause() instanceof OpenSearchException
          || thr.getCause() instanceof BatchCountMismatchException;
    }
    return false;
  }

  public record ArchiveDocIdsBatch<T>(List<String> ids, List<T> searchAfter) {
    static <T> ArchiveDocIdsBatch<T> empty() {
      return new ArchiveDocIdsBatch<>(List.of(), List.of());
    }

    static <T> ArchiveDocIdsBatch<T> from(final List<String> ids, final List<T> searchAfter) {
      return new ArchiveDocIdsBatch<>(ids, searchAfter);
    }

    public boolean isEmpty() {
      return ids.isEmpty();
    }
  }

  /**
   * Used when the number of documents processed by a reindex or delete operation does not match the
   * expected count. This is caught in the future chain to end the current execution gracefully so
   * the same batch can be retried on the next invocation.
   */
  static class BatchCountMismatchException extends RuntimeException {
    final String operation;

    BatchCountMismatchException(final String operation, final String message) {
      super(message);
      this.operation = operation;
    }
  }
}
