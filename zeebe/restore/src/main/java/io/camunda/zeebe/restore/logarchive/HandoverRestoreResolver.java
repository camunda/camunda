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
 * 1. Old and archive data are never combined on one partition.
 *
 * <p>Without any log archive or handover, this is exactly today's restore: backup ids are restored
 * as given, and a time range is resolved by {@link RestorePointResolver#resolve}.
 */
public final class HandoverRestoreResolver {

  private static final Logger LOG = LoggerFactory.getLogger(HandoverRestoreResolver.class);
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
    for (final var partition : partitions) {
      if (partition.source.completedOldBackups().contains(target)) {
        plans.put(partition.id(), new OldBackups(request.backupIds()));
        continue;
      }
      final var archived = partition.fromArchive(target);
      if (archived.isEmpty()) {
        throw new LogArchiveRestoreException(
            "Checkpoint %d cannot be restored on partition %d: it is no completed old backup and no restorable log archive checkpoint (%s). %s"
                .formatted(
                    target,
                    partition.id(),
                    partition.rejection(target),
                    partition.describeNewest()));
      }
      plans.put(partition.id(), archived.get());
    }
    final var plan = new HandoverRestorePlan(target, plans);
    LOG.info("Restoring to checkpoint {}: {}", target, plan.partitions());
    return plan;
  }

  private static HandoverRestorePlan resolveTimeRange(
      final List<Partition> partitions, final HandoverRestoreRequest request) {
    final var reference = partitions.getFirst();
    final var rejected = new LinkedHashMap<Long, String>();
    for (final var candidate : reference.candidates()) {
      final var plans = new TreeMap<Integer, PartitionPlan>();
      @Nullable String rejection = null;
      for (final var partition : partitions) {
        final var plan = partition.plan(candidate.checkpointId());
        if (plan.isEmpty()) {
          rejection =
              "partition %d: %s"
                  .formatted(partition.id(), partition.rejection(candidate.checkpointId()));
          break;
        }
        plans.put(partition.id(), plan.get());
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
      return withOldBackups(candidate.checkpointId(), plans, partitions, request);
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
      final HandoverRestoreRequest request) {
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
    LOG.info("Restoring to checkpoint {}: {}", checkpointId, plan.partitions());
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
    private final Map<Long, Optional<FromArchive>> archiveDecisions = new HashMap<>();
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
      return fromArchive(checkpointId).map(PartitionPlan.class::cast);
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
     * Where the archive restores the checkpoint on this partition, empty if no timeline can.
     *
     * @throws LogArchiveRestoreException if more than one history covers the checkpoint
     */
    Optional<FromArchive> fromArchive(final long checkpointId) {
      final var cached = archiveDecisions.get(checkpointId);
      if (cached != null) {
        return cached;
      }
      final var decision = decideArchive(checkpointId);
      archiveDecisions.put(checkpointId, decision);
      return decision;
    }

    private Optional<FromArchive> decideArchive(final long checkpointId) {
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
      rejections.put(checkpointId, String.join("; ", reasons));
      return Optional.empty();
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
