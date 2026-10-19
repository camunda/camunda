/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the log archive timelines of a partition that descend from known ones (spec section 18.5).
 *
 * <p>A restore or a rollback starts a new timeline whose id extends the one it came from ({@code
 * <tl>.r<checkpoint>}, {@code <tl>.b<backupId>}, {@code <tl>.f<seq>}) and whose first commit entry
 * names its parent. A timeline descends from a known one if its parent chain leads to it, or if its
 * id is derived from it that way; the second covers a timeline started without a parent, e.g. after
 * restoring an old backup taken before the log archive was activated.
 */
public final class LogArchiveTimelines {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveTimelines.class);
  private static final String ROOT_PREFIX = "log-archive/";
  private static final Pattern DERIVED_SUFFIX = Pattern.compile("(\\.[rbf]\\d+(-\\d+)?)+");

  private LogArchiveTimelines() {}

  /**
   * Lists the archive under every seed's id and reads the timelines that descend from the seeds of
   * each partition.
   *
   * @param seedsByPartition the known timelines of each partition, by partition id
   * @return the seeds and their descendants that exist in the archive, by partition id
   */
  public static Map<Integer, List<LogArchiveView>> discover(
      final LogArchiveStore store, final Map<Integer, Set<String>> seedsByPartition) {
    final var timelinesByPartition = new HashMap<Integer, Set<String>>();
    final var listedPrefixes = new HashSet<String>();
    seedsByPartition.values().stream()
        .flatMap(Set::stream)
        .filter(seed -> listedPrefixes.add(ROOT_PREFIX + seed))
        .forEach(
            seed ->
                store.list(ROOT_PREFIX + seed).join().stream()
                    .flatMap(key -> timelineAndPartition(key).stream())
                    .forEach(
                        entry ->
                            timelinesByPartition
                                .computeIfAbsent(entry.getValue(), id -> new TreeSet<>())
                                .add(entry.getKey())));

    final var result = new TreeMap<Integer, List<LogArchiveView>>();
    seedsByPartition.forEach(
        (partitionId, seeds) -> {
          final var views = new ArrayList<LogArchiveView>();
          for (final var timelineId : timelinesByPartition.getOrDefault(partitionId, Set.of())) {
            try {
              views.add(LogArchiveView.read(store, timelineId, partitionId));
            } catch (final LogArchiveRestoreException e) {
              LOG.warn(
                  "Ignoring log archive timeline '{}' of partition {}: {}",
                  timelineId,
                  partitionId,
                  e.getMessage());
            }
          }
          result.put(partitionId, select(views, seeds));
        });
    return result;
  }

  /**
   * The seeds and the timelines that descend from them, in id order.
   *
   * @param views the timelines of one partition
   * @param seeds the known timelines of that partition
   */
  public static List<LogArchiveView> select(
      final Collection<LogArchiveView> views, final Collection<String> seeds) {
    final var byId = new TreeMap<String, LogArchiveView>();
    views.forEach(view -> byId.put(view.timelineId(), view));
    final var selected = new LinkedHashMap<String, LogArchiveView>();
    seeds.stream().filter(byId::containsKey).forEach(id -> selected.put(id, byId.get(id)));
    boolean added = true;
    while (added) {
      added = false;
      for (final var view : byId.values()) {
        if (!selected.containsKey(view.timelineId())
            && selected.keySet().stream().anyMatch(known -> descendsFrom(view, known, byId))) {
          selected.put(view.timelineId(), view);
          added = true;
        }
      }
    }
    return new TreeMap<>(selected).values().stream().toList();
  }

  /**
   * Whether {@code view} descends from the timeline {@code ancestorId}, by its parent chain (as far
   * as it is in {@code viewsById}) or by its id.
   */
  public static boolean descendsFrom(
      final LogArchiveView view,
      final String ancestorId,
      final Map<String, LogArchiveView> viewsById) {
    if (isDerivedFrom(view.timelineId(), ancestorId)) {
      return true;
    }
    final var visited = new HashSet<String>();
    var parent = view.parentTimelineId();
    while (!parent.isEmpty() && visited.add(parent)) {
      if (parent.equals(ancestorId)) {
        return true;
      }
      final var parentView = viewsById.get(parent);
      parent = parentView == null ? "" : parentView.parentTimelineId();
    }
    return false;
  }

  /** Whether the timeline id extends the ancestor's id with restore or fork suffixes only. */
  static boolean isDerivedFrom(final String timelineId, final String ancestorId) {
    return timelineId.length() > ancestorId.length()
        && timelineId.startsWith(ancestorId)
        && DERIVED_SUFFIX.matcher(timelineId.substring(ancestorId.length())).matches();
  }

  /** {@code log-archive/<timeline>/p<partition>/...} to (timeline, partition). */
  private static Optional<Map.Entry<String, Integer>> timelineAndPartition(final String key) {
    if (!key.startsWith(ROOT_PREFIX)) {
      return Optional.empty();
    }
    final var parts = key.substring(ROOT_PREFIX.length()).split("/", 3);
    if (parts.length < 3 || parts[1].length() < 2 || parts[1].charAt(0) != 'p') {
      return Optional.empty();
    }
    try {
      return Optional.of(Map.entry(parts[0], Integer.parseInt(parts[1].substring(1))));
    } catch (final NumberFormatException e) {
      return Optional.empty();
    }
  }
}
