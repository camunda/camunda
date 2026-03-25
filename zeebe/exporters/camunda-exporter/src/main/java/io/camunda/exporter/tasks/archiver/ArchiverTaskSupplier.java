/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.zeebe.util.function.TriFunction;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ArchiverTaskSupplier<SortFieldType> {

  final String sourceIdx;
  final String destinationIdx;

  final Function<List<SortFieldType>, CompletableFuture<DocIdsSearchResponse<SortFieldType>>>
      idsSupplier;
  final TriFunction<String, String, List<String>, CompletableFuture<Long>> reindexer;
  final BiFunction<String, List<String>, CompletableFuture<Long>> deleter;

  final Executor executor;

  final AtomicBoolean finished = new AtomicBoolean(false);
  final AtomicReference<DocIdsSearchResponse<SortFieldType>> lastSearchResponse =
      new AtomicReference<>(null);
  final AtomicLong totalArchived = new AtomicLong(0);

  final Counters counters = new Counters();

  public ArchiverTaskSupplier(
      final String sourceIdx,
      final String destinationIdx,
      final Function<List<SortFieldType>, CompletableFuture<DocIdsSearchResponse<SortFieldType>>>
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

  public CompletableFuture<Long> getNextBatch() {
    counters.request();
    return idsSupplier
        .apply(List.of())
        .thenCompose(
            response -> {
              // we can set this in another stage (like after reindex/delete if we want retries)
              lastSearchResponse.set(response);
              counters.search(response.ids().size());

              if (response.isEmpty()) {
                finished.set(true);
                return CompletableFuture.completedFuture(0L);
              }

              return reindexer
                  .apply(sourceIdx, destinationIdx, response.ids())
                  .thenCompose(
                      count -> {
                        counters.reindex(count);
                        return deleter.apply(sourceIdx, response.ids());
                      })
                  .thenApply(
                      count -> {
                        counters.delete(count);
                        return count;
                      });
            });
  }

  public void printStatus() {
    System.out.println(
        ">>> The archiver task supplier for "
            + sourceIdx
            + " to "
            + destinationIdx
            + " produced "
            + counters.status());
  }

  public record DocIdsSearchResponse<T>(List<String> ids, List<T> searchAfter) {
    static <T> DocIdsSearchResponse<T> empty() {
      return new DocIdsSearchResponse<>(List.of(), List.of());
    }

    static <T> DocIdsSearchResponse<T> from(final List<String> ids, final List<T> searchAfter) {
      return new DocIdsSearchResponse<>(ids, searchAfter);
    }

    public boolean isEmpty() {
      return ids.isEmpty();
    }
  }

  public record Counters(
      AtomicInteger req, AtomicLong search, AtomicLong reindex, AtomicLong deleted) {

    public Counters() {
      this(new AtomicInteger(0), new AtomicLong(0), new AtomicLong(0), new AtomicLong(0));
    }

    public void request() {
      req.incrementAndGet();
    }

    public void search(final long resp) {
      search.accumulateAndGet(resp, Long::sum);
    }

    public void reindex(final long resp) {
      reindex.accumulateAndGet(resp, Long::sum);
    }

    public void delete(final long resp) {
      deleted.accumulateAndGet(resp, Long::sum);
    }

    public String status() {
      return "supplied "
          + req.get()
          + " tasks, "
          + "Search returned "
          + search.get()
          + " docs, "
          + "Reindexed "
          + reindex.get()
          + " docs and "
          + "Deleted "
          + deleted.get()
          + " docs in total.";
    }
  }
}
