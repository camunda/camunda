/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionLogArchiveState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * What the backup loop of the {@link CheckpointScheduler} does on a tick, for the handover from
 * continuous backup to the log archive (spec section 18.4). Derived from a single checkpoint state
 * query only, never from the scheduler's memory, so a scheduler that moves to another broker
 * continues where the previous one stopped.
 *
 * <p>The partitions are those that appear in any group of the query. The rules, first match wins:
 *
 * <table>
 *   <tr><th>#</th><th>Condition</th><th>Action</th></tr>
 *   <tr><td>0</td><td>log archive disabled</td><td>{@link Action#SCHEDULED_BACKUP}</td></tr>
 *   <tr><td>1</td><td>a partition has no log archive state or no started timeline (activation is
 *       a separate loop)</td><td>{@link Action#SCHEDULED_BACKUP}</td></tr>
 *   <tr><td>2</td><td>a handover is recorded on every partition</td>
 *       <td>{@link Action#SCHEDULED_BACKUP}: still restore points, served from the archive</td></tr>
 *   <tr><td>-</td><td>partitions recorded different handovers (not expected without a restore)
 *       </td><td>{@link Action#SCHEDULED_BACKUP}, nothing can complete the handover</td></tr>
 *   <tr><td>3s</td><td>a handover H is recorded on some partitions only, and every partition
 *       without it was added after the handover: its log archive starts at the partition's first
 *       record</td>
 *       <td>{@link Action#COMPLETE_HANDOVER_AND_BACKUP} H: such a partition took no part in the
 *       handover and records H from its start once a snapshot is archived; backups go on
 *       meanwhile, as no partition waits for H to be its last confirmed backup</td></tr>
 *   <tr><td>3a</td><td>a handover H is recorded on some partitions only</td>
 *       <td>{@link Action#COMPLETE_HANDOVER} H: the first request was only sent once H was
 *       confirmed and archived everywhere, so the remaining partitions only missed the request
 *       </td></tr>
 *   <tr><td>3b</td><td>no handover recorded; the last confirmed backup of every partition is the
 *       same HANDOVER backup H and the archived position has reached H's position on every
 *       partition</td><td>{@link Action#COMPLETE_HANDOVER} H</td></tr>
 *   <tr><td>4</td><td>no handover recorded; on every partition the archived position has reached
 *       the last confirmed backup's position (if any) and an archived snapshot exists</td>
 *       <td>{@link Action#HANDOVER_BACKUP}</td></tr>
 *   <tr><td>5</td><td>otherwise, e.g. H failed or is not confirmed or archived everywhere yet</td>
 *       <td>{@link Action#SCHEDULED_BACKUP}; a new H is taken with a new id once row 4 holds</td>
 *   </tr>
 * </table>
 *
 * <p>Backups are taken on schedule only. COMPLETE_HANDOVER is sent right away and retried with
 * backoff until recorded everywhere (a partition that recorded it rejects a repeat). With {@link
 * Action#COMPLETE_HANDOVER}, no backup is taken meanwhile, since a newer confirmed backup would
 * hide H from row 3b.
 *
 * <p>A partition added by a scale-up after the handover is first activated (row 1 until then; it
 * takes old-style backups meanwhile), then matches row 3s until it recorded H, then row 2. Row 3s
 * tells such a partition apart by its first archived position only: the partitions that took part
 * in the handover started their timeline at activation, after their first record, unless they were
 * activated before anything was compacted. Such a partition still records H by its HANDOVER backup,
 * which later backups do not hide from it: it checks its latest HANDOVER backup, not its latest
 * backup. A partition that has archived nothing yet matches row 3a until its first cut, seconds.
 */
record HandoverDecision(Action action, long checkpointId) {

  static final long NO_CHECKPOINT = -1L;

  /** The position of the first record of every partition's log. */
  private static final long FIRST_LOG_POSITION = 1L;

  private static final HandoverDecision SCHEDULED =
      new HandoverDecision(Action.SCHEDULED_BACKUP, NO_CHECKPOINT);
  private static final HandoverDecision HANDOVER =
      new HandoverDecision(Action.HANDOVER_BACKUP, NO_CHECKPOINT);

  static HandoverDecision decide(
      final CheckpointStateResponse state, final boolean logArchiveEnabled) {
    if (!logArchiveEnabled) {
      return SCHEDULED;
    }

    final Map<Integer, PartitionLogArchiveState> archives =
        byPartition(state.getLogArchiveStates(), PartitionLogArchiveState::partitionId);
    final Map<Integer, PartitionCheckpointState> backups =
        byPartition(state.getBackupStates(), PartitionCheckpointState::partitionId);
    final Set<Integer> partitions =
        Stream.of(
                archives.keySet().stream(),
                backups.keySet().stream(),
                state.getCheckpointStates().stream().map(PartitionCheckpointState::partitionId))
            .flatMap(Function.identity())
            .collect(Collectors.toCollection(TreeSet::new));

    // 1
    if (partitions.isEmpty()
        || !archives.keySet().containsAll(partitions)
        || archives.values().stream().anyMatch(a -> !a.timelineStarted())) {
      return SCHEDULED;
    }
    // from here on, archives holds exactly one state per partition

    final Set<Long> recordedHandovers =
        archives.values().stream()
            .map(PartitionLogArchiveState::handoverCheckpointId)
            .filter(id -> id != NO_CHECKPOINT)
            .collect(Collectors.toSet());
    final boolean recordedEverywhere =
        archives.values().stream().allMatch(a -> a.handoverCheckpointId() != NO_CHECKPOINT);

    // 2 and different handovers
    if (recordedEverywhere || recordedHandovers.size() > 1) {
      return SCHEDULED;
    }

    // 3s and 3a
    if (recordedHandovers.size() == 1) {
      final long recorded = recordedHandovers.iterator().next();
      final boolean onlyAddedAfterTheHandover =
          archives.values().stream()
              .filter(archive -> archive.handoverCheckpointId() == NO_CHECKPOINT)
              .allMatch(archive -> archive.firstArchivedPosition() == FIRST_LOG_POSITION);
      return new HandoverDecision(
          onlyAddedAfterTheHandover
              ? Action.COMPLETE_HANDOVER_AND_BACKUP
              : Action.COMPLETE_HANDOVER,
          recorded);
    }

    // 3b
    final var handover = commonHandoverBackup(partitions, backups);
    if (handover != null
        && archives.values().stream()
            .allMatch(archive -> hasArchivedLastBackup(archive, backups))) {
      return new HandoverDecision(Action.COMPLETE_HANDOVER, handover);
    }

    // 4
    final boolean readyForHandover =
        archives.values().stream()
            .allMatch(
                archive ->
                    archive.newestArchivedSnapshotProcessedPosition() >= 0
                        && hasArchivedLastBackup(archive, backups));
    // 5
    return readyForHandover ? HANDOVER : SCHEDULED;
  }

  /** The id of the HANDOVER backup that is the last confirmed backup of every partition, if any. */
  private static @Nullable Long commonHandoverBackup(
      final Set<Integer> partitions, final Map<Integer, PartitionCheckpointState> backups) {
    final Set<Long> ids = new TreeSet<>();
    for (final int partition : partitions) {
      final var backup = backups.get(partition);
      if (backup == null || backup.checkpointType() != CheckpointType.HANDOVER) {
        return null;
      }
      ids.add(backup.checkpointId());
    }
    return ids.size() == 1 ? ids.iterator().next() : null;
  }

  /** Whether the archive has reached the partition's last confirmed backup; true if none. */
  private static boolean hasArchivedLastBackup(
      final PartitionLogArchiveState archive,
      final Map<Integer, PartitionCheckpointState> backups) {
    final var backup = backups.get(archive.partitionId());
    return backup == null || archive.archivedPosition() >= backup.checkpointPosition();
  }

  private static <T> Map<Integer, T> byPartition(
      final Set<T> states, final Function<T, Integer> partitionId) {
    final Map<Integer, T> map = new HashMap<>();
    states.forEach(state -> map.put(partitionId.apply(state), state));
    return map;
  }

  /** Whether COMPLETE_HANDOVER is sent on this tick. */
  boolean completesHandover() {
    return action == Action.COMPLETE_HANDOVER || action == Action.COMPLETE_HANDOVER_AND_BACKUP;
  }

  /** Whether a backup is taken on this tick when one is due. */
  boolean takesBackup() {
    return action != Action.COMPLETE_HANDOVER;
  }

  /** The checkpoint type of the backup to take when one is due. */
  CheckpointType backupType() {
    return action == Action.HANDOVER_BACKUP
        ? CheckpointType.HANDOVER
        : CheckpointType.SCHEDULED_BACKUP;
  }

  enum Action {
    /** Take a backup of type {@link CheckpointType#SCHEDULED_BACKUP} when due. */
    SCHEDULED_BACKUP,
    /** Take a backup of type {@link CheckpointType#HANDOVER} when due. */
    HANDOVER_BACKUP,
    /** Send COMPLETE_HANDOVER for {@link #checkpointId()} to every partition now. */
    COMPLETE_HANDOVER,
    /**
     * Take a backup of type {@link CheckpointType#SCHEDULED_BACKUP} when due, and send
     * COMPLETE_HANDOVER for {@link #checkpointId()} to every partition now.
     */
    COMPLETE_HANDOVER_AND_BACKUP
  }
}
