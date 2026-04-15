/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import com.google.common.base.Stopwatch;
import io.camunda.zeebe.util.function.TriFunction;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;

public class ArchiveByIdTaskSupplier<SortFieldType> {
  private final String sourceIdx;
  private final String destinationIdx;
  private final Function<List<SortFieldType>, CompletableFuture<ArchiveDocIdsBatch<SortFieldType>>>
      idsSupplier;
  private final TriFunction<String, String, List<String>, CompletableFuture<Long>> reindexer;
  private final BiFunction<String, List<String>, CompletableFuture<Long>> deleter;
  private final Executor executor;
  private final Logger logger;

  private final AtomicReference<ArchiveDocIdsBatch<SortFieldType>> lastSearchResponse =
      new AtomicReference<>(null);
  private final AtomicBoolean finished = new AtomicBoolean(false);

  private final AtomicLong totalArchived = new AtomicLong(0);
  private final AtomicLong totalTimeTakenMs = new AtomicLong(0);

  public ArchiveByIdTaskSupplier(
      final String sourceIdx,
      final String destinationIdx,
      final Function<List<SortFieldType>, CompletableFuture<ArchiveDocIdsBatch<SortFieldType>>>
          idsSupplier,
      final TriFunction<String, String, List<String>, CompletableFuture<Long>> reindexer,
      final BiFunction<String, List<String>, CompletableFuture<Long>> deleter,
      final Executor executor,
      final Logger logger) {
    this.sourceIdx = sourceIdx;
    this.destinationIdx = destinationIdx;
    this.idsSupplier = idsSupplier;
    this.reindexer = reindexer;
    this.deleter = deleter;
    this.executor = executor;
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
              // we can set this in another stage (like after reindex/delete if we want retries)
              lastSearchResponse.set(response);

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
                        return deletedCount;
                      })
                  .exceptionally(
                      ex -> {
                        final var cause = ex instanceof CompletionException ? ex.getCause() : ex;
                        if (cause instanceof final BatchCountMismatchException countMismatchEx) {
                          logger.debug(
                              "Processed batch count during '{}' stage doesn't match expected doc "
                                  + "count when archiving docs from '{}' to '{}'.",
                              countMismatchEx.operation,
                              sourceIdx,
                              destinationIdx);
                          return 0L;
                        }
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
