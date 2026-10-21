/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.model.MaintenanceEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the maintenance log of a timeline in mode B on top of its commit log (spec sections 25.3
 * and 25.4): {@code COMPACTED} replaces the objects in a range by one object, {@code REMOVED} drops
 * a prefix. Every entry is checked against the rules of 25.3; a violation fails the restore.
 */
final class MaintenanceFold {

  private static final Logger LOG = LoggerFactory.getLogger(MaintenanceFold.class);

  private final LogArchiveView view;
  private final TreeMap<Long, long[]> history;
  private final List<LogObjectJson> current = new ArrayList<>();
  private boolean earlierCommitsScanned;

  private MaintenanceFold(final LogArchiveView view, final TreeMap<Long, long[]> history) {
    this.view = view;
    this.history = history;
  }

  /**
   * Reads {@code maintenance/<seq>.json} from seq 1 until one is missing and applies the entries to
   * the objects of the commit log.
   *
   * @param history the archived index and the hold after each commit seq the fold of the commit log
   *     visited, as {@code {archivedIndex, holdIndex}}
   * @return the current objects of the timeline, in index order
   */
  static List<LogObjectJson> apply(final LogArchiveView view, final TreeMap<Long, long[]> history) {
    final var fold = new MaintenanceFold(view, history);
    for (final var chunk : view.chunks()) {
      fold.current.addAll(view.readChunk(chunk).objects());
    }
    fold.current.addAll(view.openObjects());

    long seq = 1;
    while (true) {
      final var bytes =
          view.store()
              .getIfExists(view.partitionPrefix() + LogArchiveKeys.maintenanceKey(seq))
              .join();
      if (bytes.isEmpty()) {
        break;
      }
      fold.apply(LogArchiveJson.read(bytes.get(), MaintenanceEntry.class), seq);
      seq++;
    }
    return List.copyOf(fold.current);
  }

  /**
   * A timeline in mode A has no maintenance log; one that exists anyway is ignored. Probing seq 1
   * is enough to notice it without listing.
   */
  static void warnIfPresent(final LogArchiveView view) {
    final var first =
        view.store().getIfExists(view.partitionPrefix() + LogArchiveKeys.maintenanceKey(1)).join();
    if (first.isPresent()) {
      LOG.warn(
          "Ignoring the maintenance log of partition {} of timeline '{}': the timeline compacts in mode CLUSTER",
          view.partitionId(),
          view.timelineId());
    }
  }

  private void apply(final MaintenanceEntry entry, final long seq) {
    if (entry.seq() != seq
        || entry.partitionId() != view.partitionId()
        || !entry.timelineId().equals(view.timelineId())) {
      throw violation(
          seq,
          "the entry describes seq %d of partition %d of timeline '%s'"
              .formatted(entry.seq(), entry.partitionId(), entry.timelineId()));
    }
    final long base = entry.basedOnCommitSeq();
    if (base < 1 || base > view.lastSeq()) {
      throw violation(
          seq,
          "it is based on commit seq %d, but the commit log read ends at seq %d (retry the restore if the archive is live)"
              .formatted(base, view.lastSeq()));
    }
    final var state = stateAt(base);
    switch (entry.type()) {
      case COMPACTED -> applyCompacted(entry, seq, state[0]);
      case REMOVED -> applyRemoved(entry, seq, state[1]);
      case null -> throw violation(seq, "unknown maintenance entry type");
    }
  }

  private void applyCompacted(
      final MaintenanceEntry entry, final long seq, final long archivedIndex) {
    final var object = entry.object();
    if (object == null || object.lastIndex() < object.firstIndex()) {
      throw violation(seq, "COMPACTED needs an object with a valid index range");
    }
    if (object.lastIndex() > archivedIndex) {
      throw violation(
          seq,
          "COMPACTED object %s ends at index %d, above the archived index %d as of commit seq %d"
              .formatted(
                  object.key(), object.lastIndex(), archivedIndex, entry.basedOnCommitSeq()));
    }
    int from = -1;
    int to = -1;
    for (int i = 0; i < current.size(); i++) {
      final var existing = current.get(i);
      if (existing.firstIndex() >= object.firstIndex()
          && existing.lastIndex() <= object.lastIndex()) {
        if (from < 0) {
          from = i;
        } else if (existing.firstIndex() != current.get(to).lastIndex() + 1) {
          throw violation(
              seq,
              "the replaced range [%d, %d] is not contiguous"
                  .formatted(object.firstIndex(), object.lastIndex()));
        }
        to = i;
      }
    }
    if (from < 0
        || current.get(from).firstIndex() != object.firstIndex()
        || current.get(to).lastIndex() != object.lastIndex()) {
      throw violation(
          seq,
          "COMPACTED range [%d, %d] does not start and end at the boundaries of current objects"
              .formatted(object.firstIndex(), object.lastIndex()));
    }
    current.subList(from, to + 1).clear();
    current.add(from, object);
  }

  private void applyRemoved(final MaintenanceEntry entry, final long seq, final long holdIndex) {
    final var upTo = entry.upToIndex();
    if (upTo == null) {
      throw violation(seq, "REMOVED needs an upToIndex");
    }
    if (upTo >= holdIndex) {
      throw violation(
          seq,
          "REMOVED up to index %d reaches the restore hold %d as of commit seq %d"
              .formatted(upTo, holdIndex, entry.basedOnCommitSeq()));
    }
    final int count = (int) current.stream().filter(o -> o.lastIndex() <= upTo).count();
    if (count == 0 || current.get(count - 1).lastIndex() != upTo) {
      throw violation(
          seq,
          "REMOVED up to index %d does not end at the last index of a current object"
              .formatted(upTo));
    }
    current.subList(0, count).clear();
  }

  /** {archivedIndex, holdIndex} after the commit with the given seq. */
  private long[] stateAt(final long commitSeq) {
    var state = history.get(commitSeq);
    if (state == null && !earlierCommitsScanned) {
      scanEarlierCommits();
      state = history.get(commitSeq);
    }
    if (state == null) {
      throw new LogArchiveRestoreException(
          "Cannot determine the state of commit seq %d of partition %d of timeline '%s'"
              .formatted(commitSeq, view.partitionId(), view.timelineId()));
    }
    return state;
  }

  /** The commits before the root are only needed if a maintenance entry is based on one. */
  private void scanEarlierCommits() {
    earlierCommitsScanned = true;
    long archivedIndex = -1;
    long holdIndex = -1;
    for (long seq = 1; seq < view.rootSeq(); seq++) {
      final var bytes =
          view.store().getIfExists(view.partitionPrefix() + LogArchiveKeys.commitKey(seq)).join();
      if (bytes.isEmpty()) {
        return;
      }
      final var commit = LogArchiveJson.read(bytes.get(), CommitEntry.class);
      switch (commit.type()) {
        case TIMELINE_STARTED -> {
          archivedIndex = commit.startIndex() == null ? -1 : commit.startIndex() - 1;
          holdIndex = commit.startIndex() == null ? -1 : commit.startIndex();
        }
        case LOG_ARCHIVED -> archivedIndex = commit.archivedIndex();
        case HOLD_MOVED -> holdIndex = commit.holdIndex() == null ? holdIndex : commit.holdIndex();
        default -> {}
      }
      history.putIfAbsent(seq, new long[] {archivedIndex, holdIndex});
    }
  }

  private LogArchiveRestoreException violation(final long seq, final String detail) {
    return new LogArchiveRestoreException(
        "Invalid maintenance entry %d of partition %d of timeline '%s': %s"
            .formatted(seq, view.partitionId(), view.timelineId(), detail));
  }
}
