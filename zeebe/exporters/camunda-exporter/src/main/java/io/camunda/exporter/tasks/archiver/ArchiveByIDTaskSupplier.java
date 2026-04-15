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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ArchiveByIDTaskSupplier<SortFieldType> {
  private final String sourceIdx;
  private final String destinationIdx;
  private final Function<List<SortFieldType>, CompletableFuture<ArchiveDocIdsBatch<SortFieldType>>>
      idsSupplier;
  private final TriFunction<String, String, List<String>, CompletableFuture<Long>> reindexer;
  private final BiFunction<String, List<String>, CompletableFuture<Long>> deleter;
  private final Executor executor;

  private final AtomicReference<ArchiveDocIdsBatch<SortFieldType>> lastSearchResponse =
      new AtomicReference<>(null);
  private final AtomicBoolean finished = new AtomicBoolean(false);

  private final AtomicLong totalArchived = new AtomicLong(0);
  private final AtomicLong totalTimeTakenMs = new AtomicLong(0);

  public ArchiveByIDTaskSupplier(
      final String sourceIdx,
      final String destinationIdx,
      final Function<List<SortFieldType>, CompletableFuture<ArchiveDocIdsBatch<SortFieldType>>>
          idsSupplier,
      final TriFunction<String, String, List<String>, CompletableFuture<Long>> reindexer,
      final BiFunction<String, List<String>, CompletableFuture<Long>> deleter,
      final Executor executor) {
    this.sourceIdx = sourceIdx;
    this.destinationIdx = destinationIdx;
    this.idsSupplier = idsSupplier;
    this.reindexer = reindexer;
    this.deleter = deleter;
    this.executor = executor;
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

  public CompletableFuture<Long> getNextBatch() {
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

              return reindexer
                  .apply(sourceIdx, destinationIdx, response.ids())
                  .thenCompose(count -> deleter.apply(sourceIdx, response.ids()))
                  .thenApply(
                      count -> {
                        totalArchived.accumulateAndGet(count, Long::sum);
                        return count;
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
}
