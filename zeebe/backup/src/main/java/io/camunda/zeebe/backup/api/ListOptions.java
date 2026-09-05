/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Selects a page of backups ordered by checkpoint id.
 *
 * <p>Checkpoint ids are strictly increasing per partition, so ordering by checkpoint id is ordering
 * by creation time, and the checkpoint id of the last returned backup is a stable cursor for the
 * next page: backups created concurrently always get a higher id than any cursor a caller holds.
 *
 * @param order the order of checkpoint ids in the result
 * @param startExclusive the cursor. With {@link Order#DESCENDING} only backups with a smaller
 *     checkpoint id are selected, with {@link Order#ASCENDING} only backups with a larger one.
 *     Empty selects from the first checkpoint id in the given order.
 * @param limit the maximum number of distinct checkpoint ids to select. All copies of a selected
 *     checkpoint id, one per broker that stored the backup, are kept, so a result can hold more
 *     entries than {@code limit}. Empty selects all.
 */
public record ListOptions(Order order, OptionalLong startExclusive, OptionalInt limit) {

  public ListOptions {
    Objects.requireNonNull(order, "order");
    Objects.requireNonNull(startExclusive, "startExclusive");
    Objects.requireNonNull(limit, "limit");
    if (limit.isPresent() && limit.getAsInt() <= 0) {
      throw new IllegalArgumentException(
          "Expected limit to be positive, but got %d".formatted(limit.getAsInt()));
    }
  }

  /** Selects every matching backup, newest first. */
  public static ListOptions all() {
    return new ListOptions(Order.DESCENDING, OptionalLong.empty(), OptionalInt.empty());
  }

  /**
   * Selects backups newest first.
   *
   * @param before only backups with a smaller checkpoint id are selected, or empty to start at the
   *     newest backup
   * @param limit maximum number of distinct checkpoint ids, or empty for no limit
   */
  public static ListOptions newestFirst(final OptionalLong before, final OptionalInt limit) {
    return new ListOptions(Order.DESCENDING, before, limit);
  }

  /**
   * Selects backups oldest first.
   *
   * @param after only backups with a larger checkpoint id are selected, or empty to start at the
   *     oldest backup
   * @param limit maximum number of distinct checkpoint ids, or empty for no limit
   */
  public static ListOptions oldestFirst(final OptionalLong after, final OptionalInt limit) {
    return new ListOptions(Order.ASCENDING, after, limit);
  }

  /** Whether a backup with the given checkpoint id lies on the selected side of the cursor. */
  public boolean isWithinCursor(final long checkpointId) {
    if (startExclusive.isEmpty()) {
      return true;
    }
    final var start = startExclusive.getAsLong();
    return order == Order.DESCENDING ? checkpointId < start : checkpointId > start;
  }

  /** The order of checkpoint ids in a result. */
  public Comparator<Long> checkpointIdOrder() {
    return order == Order.DESCENDING ? Comparator.reverseOrder() : Comparator.naturalOrder();
  }

  /**
   * Selects the checkpoint ids of the page: distinct ids within the cursor, in the requested order,
   * at most {@code limit} of them. Stores use this to decide which manifests to read.
   */
  public List<Long> selectCheckpointIds(final Collection<Long> checkpointIds) {
    Stream<Long> selected =
        checkpointIds.stream().distinct().filter(this::isWithinCursor).sorted(checkpointIdOrder());
    if (limit.isPresent()) {
      selected = selected.limit(limit.getAsInt());
    }
    return selected.toList();
  }

  /**
   * Selects the items of the page from all matching items, ordered by checkpoint id. Items with the
   * same checkpoint id stay adjacent and keep their encounter order.
   */
  public <T> List<T> select(final Collection<T> items, final Function<T, BackupIdentifier> id) {
    final var byCheckpointId =
        items.stream()
            .collect(
                Collectors.groupingBy(
                    item -> id.apply(item).checkpointId(),
                    LinkedHashMap::new,
                    Collectors.toList()));
    return selectCheckpointIds(byCheckpointId.keySet()).stream()
        .flatMap(checkpointId -> byCheckpointId.getOrDefault(checkpointId, List.of()).stream())
        .toList();
  }

  public enum Order {
    ASCENDING,
    DESCENDING
  }
}
