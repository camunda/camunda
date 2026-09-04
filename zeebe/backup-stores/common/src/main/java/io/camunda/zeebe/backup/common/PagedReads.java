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
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/** Reads a page of manifests, synchronously, from a store's already-enumerated candidates. */
public final class PagedReads {

  private PagedReads() {}

  /**
   * Selects the page's checkpoint ids from {@code candidates} and reads each one, refilling from
   * further candidates when a selected checkpoint id's read comes back empty for every candidate
   * that shares it — the manifest was deleted between listing and this read, not the end of the
   * data. Without this, a page short by one item is indistinguishable from the true last page,
   * misleading a caller that infers "more data" from "page returned fewer than the limit".
   *
   * <p>A checkpoint id counts as read once any of its candidates (broker copies) resolves; the
   * others are still read and included, but do not consume a further unit of the page.
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
    final var byCheckpointId = new LinkedHashMap<Long, List<C>>();
    for (final var candidate : candidates) {
      byCheckpointId
          .computeIfAbsent(id.apply(candidate).checkpointId(), ignored -> new ArrayList<>())
          .add(candidate);
    }
    final var orderedCheckpointIds =
        new ListOptions(options.order(), options.startExclusive(), OptionalInt.empty())
            .selectCheckpointIds(byCheckpointId.keySet());

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
}
