/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.TimelineFile;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the log archive timelines of a partition that descend from known ones (spec section 18.5).
 *
 * <p>A restore or a rollback starts a new timeline whose first commit entry names its parent, and
 * whose registry file {@code timeline.json} (spec section 9.6) repeats it. Discovery lists the
 * timelines with one delimiter listing of {@code log-archive/} and reads one registry file per
 * timeline and partition; it never lists below the timeline level.
 *
 * <p>A timeline descends from a known one if its parent chain leads to it, or if its id is derived
 * from it ({@code <tl>.r<checkpoint>}, {@code <tl>.b<backupId>}, {@code <tl>.f<seq>}); the second
 * covers a timeline started without a parent, e.g. after restoring an old backup taken before the
 * log archive was activated.
 *
 * <p>Timelines written before the registry existed have no {@code timeline.json}. They are only
 * found by the id rule, from the seeds' ids, and their parent is not known during discovery.
 */
public final class LogArchiveTimelines {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveTimelines.class);
  private static final Pattern DERIVED_SUFFIX = Pattern.compile("(\\.[rbf]\\d+(-\\d+)?)+");

  private LogArchiveTimelines() {}

  /**
   * Lists the timelines once, reads their registry files and then the timelines that descend from
   * the seeds of each partition.
   *
   * @param seedsByPartition the known timelines of each partition, by partition id
   * @return the seeds and their descendants that exist in the archive, by partition id
   */
  public static Map<Integer, List<LogArchiveView>> discover(
      final LogArchiveStore store, final Map<Integer, Set<String>> seedsByPartition) {
    final var timelineIds = store.listChildPrefixes(LogArchiveKeys.timelinesPrefix()).join();

    final var result = new TreeMap<Integer, List<LogArchiveView>>();
    seedsByPartition.forEach(
        (partitionId, seeds) -> {
          final var parents = readParents(store, timelineIds, partitionId, seeds);
          final var views = new ArrayList<LogArchiveView>();
          for (final var timelineId : select(parents, seeds)) {
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
          result.put(partitionId, views);
        });
    return result;
  }

  /**
   * The timelines of one partition and their parents: those with a registry file, and, as a
   * fallback for older archives, those without one that are a seed or derived from a seed and hold
   * a first commit entry of the partition (their parent is unknown here, so it is empty).
   */
  private static Map<String, String> readParents(
      final LogArchiveStore store,
      final List<String> timelineIds,
      final int partitionId,
      final Set<String> seeds) {
    final var reads =
        timelineIds.stream()
            .map(
                timelineId ->
                    readParent(store, timelineId, partitionId, seeds)
                        .thenApply(parent -> Map.entry(timelineId, parent)))
            .toList();
    final var parents = new TreeMap<String, String>();
    reads.stream()
        .map(CompletableFuture::join)
        .forEach(
            entry -> entry.getValue().ifPresent(parent -> parents.put(entry.getKey(), parent)));
    return parents;
  }

  private static CompletableFuture<Optional<String>> readParent(
      final LogArchiveStore store,
      final String timelineId,
      final int partitionId,
      final Set<String> seeds) {
    final var prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);
    return store
        .getIfExists(prefix + LogArchiveKeys.timelineKey())
        .thenCompose(
            registry -> {
              if (registry.isPresent()) {
                return CompletableFuture.completedFuture(
                    Optional.of(
                        LogArchiveJson.read(registry.get(), TimelineFile.class)
                            .parentTimelineId()));
              }
              final boolean candidate =
                  seeds.stream()
                      .anyMatch(seed -> seed.equals(timelineId) || isDerivedFrom(timelineId, seed));
              if (!candidate) {
                return CompletableFuture.completedFuture(Optional.<String>empty());
              }
              return store
                  .getIfExists(prefix + LogArchiveKeys.commitKey(1))
                  .thenApply(first -> first.map(ignored -> ""));
            });
  }

  /**
   * The seeds and the timelines that descend from them, by the parent of each timeline.
   *
   * @param parents the parent of every timeline of one partition, empty if it has none
   * @param seeds the known timelines of that partition
   * @return the ids in order
   */
  static List<String> select(final Map<String, String> parents, final Collection<String> seeds) {
    final var selected = new TreeSet<String>();
    seeds.stream().filter(parents::containsKey).forEach(selected::add);
    boolean added = true;
    while (added) {
      added = false;
      for (final var id : parents.keySet()) {
        if (!selected.contains(id)
            && selected.stream().anyMatch(known -> descends(id, known, parents::get))) {
          selected.add(id);
          added = true;
        }
      }
    }
    return List.copyOf(selected);
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
    final var parents = new TreeMap<String, String>();
    byId.forEach((id, view) -> parents.put(id, view.parentTimelineId()));
    return select(parents, seeds).stream().map(byId::get).toList();
  }

  /**
   * Whether {@code view} descends from the timeline {@code ancestorId}, by its parent chain (as far
   * as it is in {@code viewsById}) or by its id.
   */
  public static boolean descendsFrom(
      final LogArchiveView view,
      final String ancestorId,
      final Map<String, LogArchiveView> viewsById) {
    return descends(
        view.timelineId(),
        ancestorId,
        id -> id.equals(view.timelineId()) ? view.parentTimelineId() : parentOf(viewsById, id));
  }

  private static @Nullable String parentOf(
      final Map<String, LogArchiveView> viewsById, final String id) {
    final var view = viewsById.get(id);
    return view == null ? null : view.parentTimelineId();
  }

  private static boolean descends(
      final String timelineId,
      final String ancestorId,
      final Function<String, @Nullable String> parentOf) {
    if (isDerivedFrom(timelineId, ancestorId)) {
      return true;
    }
    final var visited = new HashSet<String>();
    var parent = parentOf.apply(timelineId);
    while (parent != null && !parent.isEmpty() && visited.add(parent)) {
      if (parent.equals(ancestorId)) {
        return true;
      }
      parent = parentOf.apply(parent);
    }
    return false;
  }

  /** Whether the timeline id extends the ancestor's id with restore or fork suffixes only. */
  static boolean isDerivedFrom(final String timelineId, final String ancestorId) {
    return timelineId.length() > ancestorId.length()
        && timelineId.startsWith(ancestorId)
        && DERIVED_SUFFIX.matcher(timelineId.substring(ancestorId.length())).matches();
  }
}
