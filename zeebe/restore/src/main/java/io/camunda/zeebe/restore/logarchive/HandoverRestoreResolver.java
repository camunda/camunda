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
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.restore.RestorePointResolver;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.BridgedFromArchive;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.FromArchive;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.OldBackups;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.PartitionPlan;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a restore across the handover from the old backups to the log archive (spec sections
 * 18.5 and 18.5a). Pure: it only reads what it is given.
 *
 * <p>Each partition takes the path by where the target checkpoint T exists, never by comparing T
 * with the handover checkpoint: an old backup T on the partition is restored by the existing old
 * restore, otherwise the timeline that covers T is restored from the archive. After a restore to a
 * point before the handover, the new branch takes old backups again until its own handover, and
 * those come after the first handover in time.
 *
 * <p>A time range picks T among the checkpoints of both sides, the same way {@link
 * RestorePointResolver} does among old ones: T must be restorable on every partition, the one
 * closest to {@code to}, or the latest without {@code to}. With RDBMS, an archive checkpoint is
 * only restorable if it is at or after the exported position E and the archive reaches back to E +
 * 1. If the archive starts after E + 1, the partition is restored "bridged": the old backups of the
 * same branch provide the log from E + 1 up to the start of the archive, the archive the rest and
 * the snapshot (see {@link BridgedFromArchive}).
 *
 * <p>Without any log archive or handover, this is exactly today's restore: backup ids are restored
 * as given, and a time range is resolved by {@link RestorePointResolver#resolve}.
 */
public final class HandoverRestoreResolver {

  private static final Logger LOG = LoggerFactory.getLogger(HandoverRestoreResolver.class);

  /** The position of the first record of every partition's log. */
  private static final long FIRST_LOG_POSITION = 1L;

  private static final String NAME_THE_TIMELINE =
      "name the timeline to restore from with --logArchive.timeline";

  private HandoverRestoreResolver() {}

  /**
   * @param sources what each partition can be restored from, by partition id; every partition of
   *     the cluster
   * @throws LogArchiveRestoreException if no checkpoint fits, or one is ambiguous
   */
  public static HandoverRestorePlan resolve(
      final Map<Integer, PartitionRestoreSources> sources, final HandoverRestoreRequest request) {
    if (sources.isEmpty()) {
      throw new IllegalArgumentException("Expected the sources of at least one partition");
    }
    final var sorted = new TreeMap<>(sources);
    if (sorted.values().stream().noneMatch(PartitionRestoreSources::hasLogArchive)) {
      return resolveWithoutLogArchive(sorted, request);
    }
    final var partitions = new ArrayList<Partition>();
    sorted.values().forEach(source -> partitions.add(new Partition(source, request)));
    return request.byBackupId()
        ? resolveBackupId(partitions, request)
        : resolveTimeRange(partitions, request);
  }

  private static HandoverRestorePlan resolveWithoutLogArchive(
      final TreeMap<Integer, PartitionRestoreSources> sources,
      final HandoverRestoreRequest request) {
    final var plans = new TreeMap<Integer, PartitionPlan>();
    if (request.byBackupId()) {
      sources.keySet().forEach(id -> plans.put(id, new OldBackups(request.backupIds())));
      return new HandoverRestorePlan(request.targetBackupId(), plans);
    }
    final var backups =
        RestorePointResolver.resolve(
            oldMetadata(sources.values()),
            request.from(),
            request.to(),
            request.exportedPositions().isEmpty() ? null : request.exportedPositions());
    backups
        .backupsByPartitionId()
        .forEach((id, entries) -> plans.put(id, new OldBackups(checkpointIds(entries))));
    return new HandoverRestorePlan(backups.globalCheckpointId(), plans);
  }

  private static HandoverRestorePlan resolveBackupId(
      final List<Partition> partitions, final HandoverRestoreRequest request) {
    final long target = request.targetBackupId();
    final var plans = new TreeMap<Integer, PartitionPlan>();
    final var absent = new ArrayList<Integer>();
    for (final var partition : partitions) {
      if (partition.source.completedOldBackups().contains(target)) {
        plans.put(partition.id(), new OldBackups(request.backupIds()));
        continue;
      }
      final var archived = partition.fromArchive(target);
      if (archived.isPresent()) {
        plans.put(partition.id(), archived.get());
      } else if (partition.didNotExistAt(target)) {
        absent.add(partition.id());
      } else {
        throw new LogArchiveRestoreException(
            "Checkpoint %d cannot be restored on partition %d: it is no completed old backup and no restorable log archive checkpoint (%s). %s"
                .formatted(
                    target,
                    partition.id(),
                    partition.rejection(target),
                    partition.describeNewest()));
      }
    }
    final var rejection = absentNotLast(target, plans, absent);
    if (rejection != null) {
      throw new LogArchiveRestoreException(rejection);
    }
    final var plan = new HandoverRestorePlan(target, plans);
    logPlan(plan, absent);
    return plan;
  }

  /**
   * Partitions that did not exist at the checkpoint are left out of the restore, as the old restore
   * leaves out the partitions a backup does not hold: a scale-up only adds partitions after the
   * existing ones. One below a partition that holds the checkpoint means something else is wrong.
   *
   * @return why the checkpoint cannot be restored, null if it can
   */
  private static @Nullable String absentNotLast(
      final long checkpointId,
      final TreeMap<Integer, PartitionPlan> plans,
      final List<Integer> absent) {
    if (absent.isEmpty()) {
      return null;
    }
    if (plans.isEmpty()) {
      return "Checkpoint %d is restorable on no partition".formatted(checkpointId);
    }
    if (absent.getFirst() > plans.lastKey()) {
      return null;
    }
    return ("Checkpoint %d cannot be restored: partitions %s did not exist at it, but partition %d"
            + " after them holds it; only the partitions added last can be missing")
        .formatted(checkpointId, absent, plans.lastKey());
  }

  private static void logPlan(final HandoverRestorePlan plan, final List<Integer> absent) {
    if (absent.isEmpty()) {
      LOG.info("Restoring to checkpoint {}: {}", plan.checkpointId(), plan.partitions());
    } else {
      LOG.info(
          "Restoring to checkpoint {}: {}; partitions {} did not exist at it and are left out",
          plan.checkpointId(),
          plan.partitions(),
          absent);
    }
  }

  private static HandoverRestorePlan resolveTimeRange(
      final List<Partition> partitions, final HandoverRestoreRequest request) {
    final var reference = partitions.getFirst();
    final var rejected = new LinkedHashMap<Long, String>();
    for (final var candidate : reference.candidates()) {
      final var plans = new TreeMap<Integer, PartitionPlan>();
      final var absent = new ArrayList<Integer>();
      @Nullable String rejection = null;
      for (final var partition : partitions) {
        final var plan = partition.plan(candidate.checkpointId());
        if (plan.isPresent()) {
          plans.put(partition.id(), plan.get());
        } else if (partition.didNotExistAt(candidate.checkpointId())) {
          absent.add(partition.id());
        } else {
          rejection =
              "partition %d: %s"
                  .formatted(partition.id(), partition.rejection(candidate.checkpointId()));
          break;
        }
      }
      if (rejection == null) {
        rejection = absentNotLast(candidate.checkpointId(), plans, absent);
      }
      if (rejection != null) {
        rejected.put(candidate.checkpointId(), rejection);
        continue;
      }
      if (!rejected.isEmpty()) {
        LOG.info(
            "Skipped restore points before choosing checkpoint {}: {}",
            candidate.checkpointId(),
            rejected);
      }
      return withOldBackups(candidate.checkpointId(), plans, partitions, request, absent);
    }
    throw new LogArchiveRestoreException(
        "No checkpoint in [%s, %s] can be restored on every partition. %s"
            .formatted(
                request.from(),
                request.to(),
                partitions.stream().map(Partition::describeCoverage).toList()));
  }

  /** Replaces the old-side placeholders with the backups that restore them. */
  private static HandoverRestorePlan withOldBackups(
      final long checkpointId,
      final TreeMap<Integer, PartitionPlan> plans,
      final List<Partition> partitions,
      final HandoverRestoreRequest request,
      final List<Integer> absent) {
    final var oldPartitions =
        partitions.stream().filter(p -> plans.get(p.id()) instanceof OldBackups).toList();
    if (!oldPartitions.isEmpty()) {
      final var exportedPositions = new HashMap<Integer, Long>();
      oldPartitions.forEach(
          p -> {
            final var exported = request.exportedPosition(p.id());
            if (exported != null) {
              exportedPositions.put(p.id(), exported);
            }
          });
      final var backups =
          RestorePointResolver.resolveBackups(
              oldPartitions.stream().map(p -> p.source.oldMetadata().orElseThrow()).toList(),
              checkpointId,
              request.from(),
              exportedPositions.isEmpty() ? null : exportedPositions);
      backups
          .backupsByPartitionId()
          .forEach((id, entries) -> plans.put(id, new OldBackups(checkpointIds(entries))));
    }
    final var plan = new HandoverRestorePlan(checkpointId, plans);
    logPlan(plan, absent);
    return plan;
  }

  private static List<BackupMetadata> oldMetadata(
      final Collection<PartitionRestoreSources> sources) {
    return sources.stream()
        .map(
            source ->
                source
                    .oldMetadata()
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "No backup metadata found for partition " + source.partitionId())))
        .toList();
  }

  private static List<Long> checkpointIds(final List<CheckpointEntry> entries) {
    return entries.stream().map(CheckpointEntry::checkpointId).toList();
  }

  /** A restore point candidate; {@code timestamp} is null if unknown. */
  private record Candidate(long checkpointId, @Nullable Instant timestamp) {}

  /** One partition's view of both sides, with the archive decisions cached per checkpoint. */
  private static final class Partition {
    private final PartitionRestoreSources source;
    private final HandoverRestoreRequest request;
    private final @Nullable Long exportedPosition;
    private final Map<String, LogArchiveView> timelinesById = new LinkedHashMap<>();
    private final Map<Long, Optional<PartitionPlan>> archiveDecisions = new HashMap<>();
    private final Map<Long, String> rejections = new HashMap<>();
    private @Nullable TreeMap<Long, CheckpointEntry> oldRestorable;

    Partition(final PartitionRestoreSources source, final HandoverRestoreRequest request) {
      this.source = source;
      this.request = request;
      exportedPosition =
          request.byBackupId() ? null : request.exportedPosition(source.partitionId());
      source.timelines().forEach(view -> timelinesById.put(view.timelineId(), view));
    }

    int id() {
      return source.partitionId();
    }

    /** The old checkpoints a restore could end at, by today's rules. */
    TreeMap<Long, CheckpointEntry> oldRestorable() {
      if (oldRestorable == null) {
        final var restorable = new TreeMap<Long, CheckpointEntry>();
        source
            .oldMetadata()
            .map(
                metadata ->
                    RestorePointResolver.restorableCheckpoints(
                        metadata, request.from(), exportedPosition))
            .orElse(List.of())
            .forEach(entry -> restorable.put(entry.checkpointId(), entry));
        oldRestorable = restorable;
      }
      return oldRestorable;
    }

    /** The plan for the checkpoint on this partition, preferring the old backups. */
    Optional<PartitionPlan> plan(final long checkpointId) {
      if (oldRestorable().containsKey(checkpointId)) {
        // placeholder; the backups are resolved once the checkpoint is chosen
        return Optional.of(new OldBackups(List.of(checkpointId)));
      }
      return fromArchive(checkpointId);
    }

    /**
     * The restore point candidates of this partition on both sides: the restorable old checkpoints
     * and the archive's covered checkpoints, ordered like {@link RestorePointResolver} orders them.
     */
    List<Candidate> candidates() {
      final var byId = new TreeMap<Long, Candidate>();
      oldRestorable()
          .values()
          .forEach(
              entry ->
                  byId.put(
                      entry.checkpointId(),
                      new Candidate(entry.checkpointId(), entry.checkpointTimestamp())));
      for (final var view : timelinesById.values()) {
        view.checkpointsNewestFirst()
            .filter(checkpoint -> checkpoint.checkpointPosition() <= view.archivedPosition())
            .forEach(
                checkpoint -> {
                  final var timestamp =
                      checkpoint.checkpointTimestamp() < 0
                          ? null
                          : Instant.ofEpochMilli(checkpoint.checkpointTimestamp());
                  if (inTimeRange(timestamp)) {
                    byId.putIfAbsent(
                        checkpoint.checkpointId(),
                        new Candidate(checkpoint.checkpointId(), timestamp));
                  }
                });
      }
      final var to = request.to();
      final Comparator<Candidate> order =
          to == null
              ? Comparator.comparingLong(Candidate::checkpointId).reversed()
              : Comparator.<Candidate, Boolean>comparing(c -> c.timestamp() == null)
                  .thenComparing(
                      c ->
                          c.timestamp() == null
                              ? Duration.ZERO
                              : Duration.between(to, c.timestamp()).abs())
                  .thenComparing(Comparator.comparingLong(Candidate::checkpointId).reversed());
      return byId.values().stream().sorted(order).toList();
    }

    /**
     * An archive checkpoint counts from {@code from} on. Without a timestamp (archived by an older
     * version) it cannot be placed in a time range, so it only counts for the latest restore point.
     */
    private boolean inTimeRange(final @Nullable Instant timestamp) {
      if (request.from() == null && request.to() == null) {
        return true;
      }
      return timestamp != null && (request.from() == null || !timestamp.isBefore(request.from()));
    }

    /**
     * Where the archive restores the checkpoint on this partition, alone or bridged with old
     * backups; empty if no timeline can.
     *
     * @throws LogArchiveRestoreException if more than one history covers the checkpoint
     */
    Optional<PartitionPlan> fromArchive(final long checkpointId) {
      final var cached = archiveDecisions.get(checkpointId);
      if (cached != null) {
        return cached;
      }
      final var decision = decideArchive(checkpointId);
      archiveDecisions.put(checkpointId, decision);
      return decision;
    }

    private Optional<PartitionPlan> decideArchive(final long checkpointId) {
      final var occurrences = new LinkedHashMap<LogArchiveView, CoveredCheckpointJson>();
      for (final var view : timelinesById.values()) {
        view.findCheckpoint(checkpointId)
            .filter(checkpoint -> checkpoint.checkpointPosition() <= view.archivedPosition())
            .ifPresent(checkpoint -> occurrences.put(view, checkpoint));
      }
      if (occurrences.isEmpty()) {
        rejections.put(
            checkpointId, "not covered by the log archive timelines " + timelinesById.keySet());
        return Optional.empty();
      }
      final var newestFirst = oneHistoryNewestFirst(checkpointId, occurrences);
      final var exported = Optional.ofNullable(exportedPosition);
      final var reasons = new ArrayList<String>();
      for (final var view : newestFirst) {
        final var checkpoint = Objects.requireNonNull(occurrences.get(view));
        try {
          LogArchiveRestorePlanner.plan(view, checkpoint, exported);
          return Optional.of(new FromArchive(view, checkpoint, exported));
        } catch (final LogArchiveRestoreException e) {
          reasons.add(e.getMessage());
        }
      }
      for (final var view : newestFirst) {
        final var bridged = bridge(view, Objects.requireNonNull(occurrences.get(view)), reasons);
        if (bridged.isPresent()) {
          return Optional.of(bridged.get());
        }
      }
      rejections.put(checkpointId, String.join("; ", reasons));
      return Optional.empty();
    }

    /**
     * The bridged restore of the checkpoint from the timeline (spec section 18.5a), if the archive
     * alone cannot restore it only because it starts after E + 1: the old backups of the branch
     * that handed over to this timeline provide the log from E + 1 up to the start of the archive.
     *
     * <p>Same branch: the timeline records the handover H, and H is an old checkpoint at the same
     * position in the old backups' range that also covers E. Compaction was pinned to the old
     * backups until H, so they hold that branch's log without gaps up to H, and the archive started
     * before H. Old backups and archive then hold the same journal entries where they overlap.
     *
     * @param reasons collects why the bridge is impossible, for errors
     */
    private Optional<BridgedFromArchive> bridge(
        final LogArchiveView view,
        final CoveredCheckpointJson checkpoint,
        final List<String> reasons) {
      if (exportedPosition == null || !startsAfterExported(view, exportedPosition)) {
        return Optional.empty();
      }
      final var handover = view.handover();
      final var metadata = source.oldMetadata();
      if (handover.isEmpty() || metadata.isEmpty()) {
        reasons.add(
            "no bridge from old backups to timeline '%s': %s"
                .formatted(
                    view.timelineId(),
                    handover.isEmpty()
                        ? "it records no handover"
                        : "the partition has no old backups"));
        return Optional.empty();
      }
      final long handoverId = handover.get().checkpointId();
      final var handoverEntry = oldRestorable().get(handoverId);
      if (handoverEntry == null
          || handoverEntry.checkpointPosition() != handover.get().checkpointPosition()) {
        reasons.add(
            ("no bridge from old backups to timeline '%s': its handover checkpoint %d at position"
                    + " %d is no old backup restorable from the exported position %d on the same"
                    + " branch")
                .formatted(
                    view.timelineId(),
                    handoverId,
                    handover.get().checkpointPosition(),
                    exportedPosition));
        return Optional.empty();
      }
      // the first old backup that reaches into the archive, so that the two overlap at the seam
      final long firstArchived = view.firstArchivedPosition();
      final var last =
          oldRestorable().headMap(handoverId, true).values().stream()
              .filter(entry -> entry.checkpointType().shouldCreateBackup())
              .filter(entry -> entry.checkpointPosition() >= firstArchived)
              .findFirst()
              .orElse(handoverEntry);
      final List<Long> backupIds;
      try {
        LogArchiveRestorePlanner.planBridged(view, checkpoint, exportedPosition);
        final var backups =
            Objects.requireNonNull(
                RestorePointResolver.resolveBackups(
                        List.of(metadata.get()),
                        last.checkpointId(),
                        request.from(),
                        Map.of(id(), exportedPosition))
                    .backupsByPartitionId()
                    .get(id()));
        final long required = Math.max(exportedPosition + 1, FIRST_LOG_POSITION);
        if (backups.getFirst().getFirstLogPositionOrDefault() > required) {
          throw new IllegalStateException(
              "the old backup %d starts at position %d, after the exported position %d + 1"
                  .formatted(
                      backups.getFirst().checkpointId(),
                      backups.getFirst().getFirstLogPositionOrDefault(),
                      exportedPosition));
        }
        backupIds = checkpointIds(backups);
      } catch (final LogArchiveRestoreException | IllegalStateException e) {
        reasons.add(
            "no bridge from old backups to timeline '%s': %s"
                .formatted(view.timelineId(), e.getMessage()));
        return Optional.empty();
      }
      final var missing =
          backupIds.stream().filter(id -> !source.completedOldBackups().contains(id)).toList();
      if (!missing.isEmpty()) {
        reasons.add(
            "no bridge from old backups to timeline '%s': old backups %s are not completed"
                .formatted(view.timelineId(), missing));
        return Optional.empty();
      }
      return Optional.of(
          new BridgedFromArchive(backupIds, view, checkpoint, exportedPosition, view.startIndex()));
    }

    /** Whether the timeline starts after the position after E, so it alone cannot fill the gap. */
    private static boolean startsAfterExported(final LogArchiveView view, final long exported) {
      final long firstArchived = view.firstArchivedPosition();
      return view.startIndex() > 1
          && (firstArchived < 0 || firstArchived > Math.max(exported + 1, FIRST_LOG_POSITION));
    }

    /**
     * The timelines covering the checkpoint, newest first, if they share one history: a restored or
     * forked timeline re-archives its parent's log up to the fork, so the same checkpoint at the
     * same position in a descendant is the same restore point. Any other repetition, e.g. a manual
     * checkpoint id reused on two branches, is ambiguous.
     */
    private List<LogArchiveView> oneHistoryNewestFirst(
        final long checkpointId, final Map<LogArchiveView, CoveredCheckpointJson> occurrences) {
      final var views = List.copyOf(occurrences.keySet());
      final var positions =
          occurrences.values().stream()
              .map(CoveredCheckpointJson::checkpointPosition)
              .collect(Collectors.toSet());
      boolean oneHistory = positions.size() == 1;
      for (int i = 0; oneHistory && i < views.size(); i++) {
        for (int j = i + 1; oneHistory && j < views.size(); j++) {
          oneHistory = related(views.get(i), views.get(j));
        }
      }
      if (!oneHistory) {
        throw new LogArchiveRestoreException(
            "Checkpoint %d of partition %d is covered by more than one log archive timeline %s; %s"
                .formatted(
                    checkpointId,
                    id(),
                    occurrences.entrySet().stream()
                        .map(
                            e ->
                                "%s (position %d)"
                                    .formatted(
                                        e.getKey().timelineId(), e.getValue().checkpointPosition()))
                        .toList(),
                    NAME_THE_TIMELINE));
      }
      return views.stream()
          .sorted(
              Comparator.comparingLong((LogArchiveView view) -> ancestors(view, views)).reversed())
          .toList();
    }

    private boolean related(final LogArchiveView a, final LogArchiveView b) {
      return LogArchiveTimelines.descendsFrom(a, b.timelineId(), timelinesById)
          || LogArchiveTimelines.descendsFrom(b, a.timelineId(), timelinesById);
    }

    private long ancestors(final LogArchiveView view, final List<LogArchiveView> views) {
      return views.stream()
          .filter(
              other -> LogArchiveTimelines.descendsFrom(view, other.timelineId(), timelinesById))
          .count();
    }

    /**
     * Whether the partition was added by a scale-up after the checkpoint (spec section 18.4), given
     * that it cannot restore it: a timeline of it holds its whole log from the first record on, and
     * every checkpoint it knows, on both sides, is newer. Checkpoint ids only grow on a partition,
     * and a partition that existed at the checkpoint holds it in its log, so such a partition did
     * not exist yet. One that knows no checkpoint at all may only lag behind, so it does not count.
     */
    boolean didNotExistAt(final long checkpointId) {
      final var oldBackups = source.completedOldBackups();
      if (!oldBackups.isEmpty() && oldBackups.first() <= checkpointId) {
        return false;
      }
      return timelinesById.values().stream()
          .filter(view -> view.firstArchivedPosition() == FIRST_LOG_POSITION)
          .anyMatch(
              view ->
                  oldestCheckpointId(view).isPresent()
                      && oldestCheckpointId(view).getAsLong() > checkpointId);
    }

    /** The id of the oldest checkpoint the timeline covers, from the chunks' id ranges. */
    private static OptionalLong oldestCheckpointId(final LogArchiveView view) {
      for (final var chunk : view.chunks()) {
        if (chunk.firstCheckpointId() >= 0) {
          return OptionalLong.of(chunk.firstCheckpointId());
        }
      }
      return view.checkpoints().stream().mapToLong(CoveredCheckpointJson::checkpointId).min();
    }

    String rejection(final long checkpointId) {
      return rejections.getOrDefault(checkpointId, "not restorable");
    }

    /** The newest restorable checkpoint on each side, for errors. */
    String describeNewest() {
      final var newestOld =
          source.completedOldBackups().isEmpty()
              ? "none"
              : String.valueOf(source.completedOldBackups().last());
      return "Newest old backup of partition %d: %s; newest log archive checkpoint: %s"
          .formatted(id(), newestOld, newestArchived());
    }

    private String newestArchived() {
      return timelinesById.values().stream()
          .flatMap(
              view ->
                  view
                      .checkpointsNewestFirst()
                      .filter(c -> c.checkpointPosition() <= view.archivedPosition())
                      .findFirst()
                      .map(c -> "%d in '%s'".formatted(c.checkpointId(), view.timelineId()))
                      .stream())
          .toList()
          .toString();
    }

    /** What both sides of this partition cover, for errors. */
    String describeCoverage() {
      final var old = oldRestorable();
      final var oldRange =
          old.isEmpty() ? "none" : "%d..%d".formatted(old.firstKey(), old.lastKey());
      final var archived =
          timelinesById.values().stream()
              .map(
                  view ->
                      "'%s' positions %d..%d"
                          .formatted(
                              view.timelineId(),
                              view.firstArchivedPosition(),
                              view.archivedPosition()))
              .toList();
      return "partition %d (exported position %s): old checkpoints %s, log archive %s"
          .formatted(
              id(), exportedPosition == null ? "none" : exportedPosition, oldRange, archived);
    }
  }
}
