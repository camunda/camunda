/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.backup.logarchive.model.HandoverJson;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.restore.RestorePointResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * One partition's restore points as continuous backup sees them (spec section 18.5a): backup
 * metadata whose ranges and checkpoints {@link RestorePointResolver} resolves a time range on, with
 * exactly its own rules. The log archive is mapped onto it:
 *
 * <ul>
 *   <li>An archived snapshot together with the archived log up to the next covered checkpoint after
 *       it is a backup of that checkpoint: the same state and log as an old backup of it, so it is
 *       a possible start of the restored log.
 *   <li>The archive of the current branch is one range: contiguous by construction, from its first
 *       such backup to its newest covered checkpoint, which ends the range like a backup does.
 *       Covered checkpoints before the first backup are left out, as no archived snapshot restores
 *       them.
 *   <li>After a handover H, the old range that reaches H and the archive after H on the same branch
 *       form one range: the old checkpoints up to H, then the archive's after H.
 * </ul>
 *
 * <p>Only the current branch is seen, as in continuous backup, whose {@code metadata.json} holds
 * the history of the branch that wrote it last. The current branch of the archive is the timeline
 * (with its ancestors up to where it forked from them) whose newest checkpoint is the newest. If
 * the partition has old backups, only a branch that records a handover of them (or whose archive
 * holds the partition's whole log, after a scale-up) continues them; otherwise the old backups are
 * the current branch and the archive belongs to another one.
 */
final class ContinuousBackupView {

  private final BackupMetadata metadata;
  private final Set<Long> oldCheckpointIds;
  private final List<LogArchiveView> branch;

  private ContinuousBackupView(
      final BackupMetadata metadata,
      final Set<Long> oldCheckpointIds,
      final List<LogArchiveView> branch) {
    this.metadata = metadata;
    this.oldCheckpointIds = Set.copyOf(oldCheckpointIds);
    this.branch = List.copyOf(branch);
  }

  static ContinuousBackupView of(final PartitionRestoreSources source) {
    final var old = source.oldMetadata();
    final var oldIds = new HashSet<Long>();
    old.ifPresent(m -> m.checkpoints().forEach(c -> oldIds.add(c.checkpointId())));

    final var branch = currentBranch(source.timelines(), old.orElse(null));
    if (branch.isEmpty()) {
      return new ContinuousBackupView(
          old.orElseGet(() -> empty(source.partitionId())), oldIds, List.of());
    }
    final var history = history(branch);
    final var handover = old.flatMap(m -> linkedHandover(branch, m));
    if (handover.isPresent()) {
      return combined(old.get(), handover.get(), history, branch);
    }
    return archiveOnly(source.partitionId(), branch, history);
  }

  /** The metadata to resolve a time range on. */
  BackupMetadata metadata() {
    return metadata;
  }

  /** Whether the checkpoint is one of the old backups' checkpoints in this view. */
  boolean isOld(final long checkpointId) {
    return oldCheckpointIds.contains(checkpointId);
  }

  /** The timelines of the current branch, oldest first; empty if the view has no archive. */
  List<LogArchiveView> branch() {
    return branch;
  }

  /** The old range that reaches H, continued by the archive after H. */
  private static ContinuousBackupView combined(
      final BackupMetadata old,
      final HandoverJson handover,
      final List<ArchivedCheckpoint> history,
      final List<LogArchiveView> branch) {
    final var range = rangeOf(old, handover.checkpointId()).orElseThrow();
    final var checkpoints = new TreeMap<Long, CheckpointEntry>();
    old.checkpoints().stream()
        // the old range ends at H; the archive is the source of truth after it
        .filter(
            c ->
                c.checkpointId() <= handover.checkpointId()
                    || c.checkpointId() > range.end()
                    || c.checkpointPosition() <= handover.checkpointPosition())
        .forEach(c -> checkpoints.put(c.checkpointId(), c));
    final var afterHandover =
        history.stream()
            .filter(c -> c.checkpoint().checkpointPosition() > handover.checkpointPosition())
            .toList();
    final var keptOldIds = Set.copyOf(checkpoints.keySet());
    final var archived = entries(afterHandover, handover.checkpointPosition() + 1);
    archived.forEach(entry -> checkpoints.putIfAbsent(entry.checkpointId(), entry));
    final var ranges = new ArrayList<RangeEntry>();
    for (final var entry : old.ranges()) {
      ranges.add(
          entry.equals(range) && !archived.isEmpty()
              ? new RangeEntry(range.start(), archived.getLast().checkpointId())
              : entry);
    }
    return new ContinuousBackupView(
        new BackupMetadata(
            old.version(),
            old.partitionId(),
            old.lastUpdated(),
            List.copyOf(checkpoints.values()),
            ranges),
        keptOldIds,
        branch);
  }

  /** The archive of the branch as the partition's only range. */
  private static ContinuousBackupView archiveOnly(
      final int partitionId,
      final List<LogArchiveView> branch,
      final List<ArchivedCheckpoint> history) {
    final var firstStart =
        history.stream().filter(ArchivedCheckpoint::start).findFirst().orElse(null);
    if (firstStart == null) {
      return new ContinuousBackupView(empty(partitionId), Set.of(), branch);
    }
    final var restorable = history.subList(history.indexOf(firstStart), history.size());
    final var entries = entries(restorable, branch.getFirst().firstArchivedPosition());
    final var checkpoints = new TreeMap<Long, CheckpointEntry>();
    entries.forEach(entry -> checkpoints.putIfAbsent(entry.checkpointId(), entry));
    return new ContinuousBackupView(
        new BackupMetadata(
            BackupMetadata.VERSION,
            partitionId,
            Instant.EPOCH,
            List.copyOf(checkpoints.values()),
            List.of(
                new RangeEntry(
                    entries.getFirst().checkpointId(), entries.getLast().checkpointId()))),
        Set.of(),
        branch);
  }

  /**
   * The archived checkpoints as backup metadata entries: a backup for each start and for the last
   * one, which ends the range; a marker for every other one. A backup's log starts after the
   * previous backup, as the archive is contiguous; the first one's at {@code firstLogPosition}.
   */
  private static List<CheckpointEntry> entries(
      final List<ArchivedCheckpoint> history, final long firstLogPosition) {
    final var entries = new ArrayList<CheckpointEntry>();
    long logStart = firstLogPosition;
    for (int i = 0; i < history.size(); i++) {
      final var checkpoint = history.get(i).checkpoint();
      final var timestamp = Instant.ofEpochMilli(checkpoint.checkpointTimestamp());
      if (history.get(i).start() || i == history.size() - 1) {
        entries.add(
            new CheckpointEntry(
                checkpoint.checkpointId(),
                checkpoint.checkpointPosition(),
                timestamp,
                backupType(checkpoint),
                OptionalLong.of(logStart)));
        logStart = checkpoint.checkpointPosition() + 1;
      } else {
        entries.add(
            new CheckpointEntry(
                checkpoint.checkpointId(),
                checkpoint.checkpointPosition(),
                timestamp,
                CheckpointType.MARKER,
                OptionalLong.empty()));
      }
    }
    return entries;
  }

  private static CheckpointType backupType(final CoveredCheckpointJson checkpoint) {
    try {
      final var type = CheckpointType.valueOf(checkpoint.checkpointType());
      return type.shouldCreateBackup() ? type : CheckpointType.SCHEDULED_BACKUP;
    } catch (final IllegalArgumentException | NullPointerException unknown) {
      return CheckpointType.SCHEDULED_BACKUP;
    }
  }

  /**
   * The covered checkpoints of the branch in position order: each timeline's up to where its child
   * re-archives the log, i.e. before the child's first covered checkpoint, then the child's.
   */
  private static List<ArchivedCheckpoint> history(final List<LogArchiveView> branch) {
    final var history = new ArrayList<ArchivedCheckpoint>();
    final var seen = new HashSet<Long>();
    for (int i = 0; i < branch.size(); i++) {
      final var view = branch.get(i);
      final long limit = i + 1 < branch.size() ? forkPosition(branch.get(i + 1)) : Long.MAX_VALUE;
      final var covered = covered(view);
      final var starts = starts(view, covered);
      for (final var checkpoint : covered) {
        if (checkpoint.checkpointPosition() < limit && seen.add(checkpoint.checkpointId())) {
          history.add(
              new ArchivedCheckpoint(checkpoint, starts.contains(checkpoint.checkpointId())));
        }
      }
    }
    return history;
  }

  private static long forkPosition(final LogArchiveView child) {
    final var covered = covered(child);
    if (!covered.isEmpty()) {
      return covered.getFirst().checkpointPosition();
    }
    final long firstArchived = child.firstArchivedPosition();
    return firstArchived < 0 ? Long.MAX_VALUE : firstArchived;
  }

  /**
   * The archived checkpoints of the timeline in position order. One archived by an older version
   * has no timestamp and cannot be placed in time, so it is left out.
   */
  private static List<CoveredCheckpointJson> covered(final LogArchiveView view) {
    return view.checkpointsNewestFirst()
        .filter(c -> c.checkpointPosition() >= 0)
        .filter(c -> c.checkpointPosition() <= view.archivedPosition())
        .filter(c -> c.checkpointTimestamp() >= 0)
        .sorted(Comparator.comparingLong(CoveredCheckpointJson::checkpointPosition))
        .toList();
  }

  /**
   * The checkpoints that an archived snapshot is the newest restorable snapshot below: for each
   * snapshot, the first covered checkpoint after its processed, last follow-up event and max
   * exported positions (as the planner selects snapshots). A timeline that starts at the first
   * record restores its first checkpoint without any snapshot.
   */
  private static Set<Long> starts(
      final LogArchiveView view, final List<CoveredCheckpointJson> covered) {
    final var snapshotPositions = new ArrayList<Long>();
    if (view.startIndex() == 1) {
      snapshotPositions.add(0L);
    }
    view.snapshots()
        .forEach(
            snapshot ->
                snapshotPositions.add(
                    Stream.of(
                            snapshot.processedPosition(),
                            snapshot.lastFollowupEventPosition(),
                            snapshot.maxExportedPosition())
                        .max(Long::compare)
                        .orElseThrow()));
    final var starts = new HashSet<Long>();
    for (final long snapshotPosition : snapshotPositions) {
      covered.stream()
          .filter(c -> c.checkpointPosition() > snapshotPosition)
          .findFirst()
          .ifPresent(c -> starts.add(c.checkpointId()));
    }
    return starts;
  }

  /**
   * The current branch of the archive, oldest timeline first; empty if the old backups are the
   * current branch or there is no archive.
   */
  private static List<LogArchiveView> currentBranch(
      final List<LogArchiveView> timelines, final @Nullable BackupMetadata old) {
    final var byId = new LinkedHashMap<String, LogArchiveView>();
    timelines.forEach(view -> byId.put(view.timelineId(), view));
    final var parents = new HashMap<String, LogArchiveView>();
    byId.values()
        .forEach(
            view -> {
              final var parent = parentOf(view, byId);
              if (parent != null) {
                parents.put(view.timelineId(), parent);
              }
            });
    final var parentIds = new HashSet<String>();
    parents.values().forEach(parent -> parentIds.add(parent.timelineId()));

    List<LogArchiveView> current = List.of();
    long newest = Long.MIN_VALUE;
    for (final var leaf : byId.values()) {
      if (parentIds.contains(leaf.timelineId())) {
        continue;
      }
      final var branch = branchOf(leaf, parents);
      if (old != null
          && linkedHandover(branch, old).isEmpty()
          && branch.getFirst().firstArchivedPosition() != 1) {
        continue; // the old backups do not continue in this branch
      }
      final long newestTimestamp =
          history(branch).stream()
              .mapToLong(c -> c.checkpoint().checkpointTimestamp())
              .max()
              .orElse(-1L);
      if (current.isEmpty() || newestTimestamp > newest) {
        current = branch;
        newest = newestTimestamp;
      }
    }
    return current;
  }

  private static List<LogArchiveView> branchOf(
      final LogArchiveView leaf, final Map<String, LogArchiveView> parents) {
    final var branch = new ArrayList<LogArchiveView>();
    final var visited = new HashSet<String>();
    @Nullable LogArchiveView view = leaf;
    while (view != null && visited.add(view.timelineId())) {
      branch.addFirst(view);
      view = parents.get(view.timelineId());
    }
    return branch;
  }

  /** The parent by the registry, or else the nearest timeline its id is derived from. */
  private static @Nullable LogArchiveView parentOf(
      final LogArchiveView view, final Map<String, LogArchiveView> byId) {
    final var registered = byId.get(view.parentTimelineId());
    if (registered != null && registered != view) {
      return registered;
    }
    return byId.values().stream()
        .filter(other -> LogArchiveTimelines.isDerivedFrom(view.timelineId(), other.timelineId()))
        .max(Comparator.comparingInt(other -> other.timelineId().length()))
        .orElse(null);
  }

  /**
   * The newest handover recorded on the branch that is an old checkpoint at the same position in a
   * range of the old backups: the link from those old backups to this branch.
   */
  private static Optional<HandoverJson> linkedHandover(
      final List<LogArchiveView> branch, final BackupMetadata old) {
    for (final var view : branch.reversed()) {
      final var handover = view.handover();
      if (handover.isEmpty() || handover.get().checkpointPosition() <= 0) {
        continue;
      }
      final long id = handover.get().checkpointId();
      final boolean samePosition =
          old.checkpoints().stream()
              .anyMatch(
                  c ->
                      c.checkpointId() == id
                          && c.checkpointPosition() == handover.get().checkpointPosition());
      if (samePosition && rangeOf(old, id).isPresent()) {
        return handover;
      }
    }
    return Optional.empty();
  }

  private static Optional<RangeEntry> rangeOf(final BackupMetadata old, final long checkpointId) {
    return old.ranges().stream()
        .filter(range -> range.start() <= checkpointId && checkpointId <= range.end())
        .findFirst();
  }

  private static BackupMetadata empty(final int partitionId) {
    return new BackupMetadata(
        BackupMetadata.VERSION, partitionId, Instant.EPOCH, List.of(), List.of());
  }

  /** A covered checkpoint of the branch, and whether an archived snapshot starts it. */
  private record ArchivedCheckpoint(CoveredCheckpointJson checkpoint, boolean start) {}
}
