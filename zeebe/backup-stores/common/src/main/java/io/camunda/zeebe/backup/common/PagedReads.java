/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.ListOptions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Reads a page of manifests from a store's already-enumerated candidates, refilling from further
 * candidates when a selected checkpoint id's read comes back empty for every candidate that shares
 * it — the manifest was deleted between listing and this read, not the end of the data. Without
 * this, a page short by one item is indistinguishable from the true last page, misleading a caller
 * that infers "more data" from "page returned fewer than the limit".
 *
 * <p>A checkpoint id counts as read once any of its candidates (broker copies) resolves; the others
 * are still read and included, but do not consume a further unit of the page.
 */
public final class PagedReads {

  private PagedReads() {}

  /**
   * Synchronous variant: reads one checkpoint id at a time, in candidate order. Suited to a local,
   * effectively free read (e.g. a filesystem store) where concurrency would not pay for itself.
   *
   * @param candidates every candidate matching the wildcard, not yet limited to one page
   * @param id the checkpoint id a candidate belongs to
   * @param options the page to select, by order, cursor and limit on distinct checkpoint ids
   * @param read reads one candidate, empty if it no longer exists
   */
  public static <C, R> List<R> readPage(
      final Collection<C> candidates,
      final Function<C, BackupIdentifier> id,
      final ListOptions options,
      final Function<C, Optional<R>> read) {
    final var byCheckpointId = groupByCheckpointId(candidates, id);
    final var orderedCheckpointIds =
        orderedCandidateCheckpointIds(byCheckpointId.keySet(), options);

    final var results = new ArrayList<R>();
    var remaining = options.limit().orElse(orderedCheckpointIds.size());
    var index = 0;
    while (remaining > 0 && index < orderedCheckpointIds.size()) {
      final var resolved =
          byCheckpointId.getOrDefault(orderedCheckpointIds.get(index++), List.of()).stream()
              .map(read)
              .flatMap(Optional::stream)
              .toList();
      results.addAll(resolved);
      if (!resolved.isEmpty()) {
        remaining--;
      }
    }
    return results;
  }

  /**
   * Asynchronous variant: reads in waves, every checkpoint id the page currently needs at once, so
   * a store bounds the wave's concurrency (e.g. with {@link SemaphoreLeasedScheduler}) rather than
   * this helper picking a number for it. Only if a wave comes back short — one or more checkpoint
   * ids resolved to nothing — does a further, smaller wave read the next ones to refill the page.
   *
   * @param candidates every candidate matching the wildcard, not yet limited to one page
   * @param id the checkpoint id a candidate belongs to
   * @param options the page to select, by order, cursor and limit on distinct checkpoint ids
   * @param read reads one candidate, empty if it no longer exists
   */
  public static <C, R> CompletableFuture<List<R>> readPageAsync(
      final Collection<C> candidates,
      final Function<C, BackupIdentifier> id,
      final ListOptions options,
      final Function<C, CompletableFuture<Optional<R>>> read) {
    final var byCheckpointId = groupByCheckpointId(candidates, id);
    final var orderedCheckpointIds =
        orderedCandidateCheckpointIds(byCheckpointId.keySet(), options);
    return readWave(
        byCheckpointId,
        orderedCheckpointIds,
        0,
        options.limit().orElse(orderedCheckpointIds.size()),
        read);
  }

  private static <C, R> CompletableFuture<List<R>> readWave(
      final Map<Long, List<C>> byCheckpointId,
      final List<Long> orderedCheckpointIds,
      final int index,
      final int remaining,
      final Function<C, CompletableFuture<Optional<R>>> read) {
    if (remaining <= 0 || index >= orderedCheckpointIds.size()) {
      return CompletableFuture.completedFuture(List.of());
    }
    final var window =
        orderedCheckpointIds.subList(
            index, Math.min(index + remaining, orderedCheckpointIds.size()));
    final var perCheckpointFutures =
        window.stream()
            .map(
                checkpointId ->
                    readCheckpointId(byCheckpointId.getOrDefault(checkpointId, List.of()), read))
            .toList();
    return CompletableFuture.allOf(perCheckpointFutures.toArray(CompletableFuture[]::new))
        .thenCompose(
            ignored -> {
              final var results = new ArrayList<R>();
              var found = 0;
              for (final var future : perCheckpointFutures) {
                final var resolved = future.join();
                results.addAll(resolved);
                if (!resolved.isEmpty()) {
                  found++;
                }
              }
              return readWave(
                      byCheckpointId,
                      orderedCheckpointIds,
                      index + window.size(),
                      remaining - found,
                      read)
                  .thenApply(
                      more -> {
                        results.addAll(more);
                        return results;
                      });
            });
  }

  private static <C, R> CompletableFuture<List<R>> readCheckpointId(
      final List<C> candidates, final Function<C, CompletableFuture<Optional<R>>> read) {
    final var futures = candidates.stream().map(read).toList();
    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
        .thenApply(
            ignored ->
                futures.stream().map(CompletableFuture::join).flatMap(Optional::stream).toList());
  }

  private static <C> Map<Long, List<C>> groupByCheckpointId(
      final Collection<C> candidates, final Function<C, BackupIdentifier> id) {
    final var byCheckpointId = new LinkedHashMap<Long, List<C>>();
    for (final var candidate : candidates) {
      byCheckpointId
          .computeIfAbsent(id.apply(candidate).checkpointId(), ignored -> new ArrayList<>())
          .add(candidate);
    }
    return byCheckpointId;
  }

  private static List<Long> orderedCandidateCheckpointIds(
      final Collection<Long> checkpointIds, final ListOptions options) {
    return new ListOptions(options.order(), options.startExclusive(), OptionalInt.empty())
        .selectCheckpointIds(checkpointIds);
  }
}
