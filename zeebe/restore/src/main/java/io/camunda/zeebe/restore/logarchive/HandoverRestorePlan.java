/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Where each partition is restored from to reach one checkpoint, on either side of the handover
 * (spec section 18.5).
 *
 * @param checkpointId the checkpoint all partitions are restored to
 * @param partitions the plan of each partition, by partition id
 */
public record HandoverRestorePlan(long checkpointId, SortedMap<Integer, PartitionPlan> partitions) {

  public HandoverRestorePlan {
    partitions = new TreeMap<>(partitions);
  }

  /** Whether every partition is restored from old backups, exactly as without a log archive. */
  public boolean allFromOldBackups() {
    return partitions.values().stream().allMatch(OldBackups.class::isInstance);
  }

  /** The old backups of the partitions restored from them, by partition id. */
  public Map<Integer, long[]> oldBackupIds() {
    final var backupIds = new TreeMap<Integer, long[]>();
    partitions.forEach(
        (partitionId, plan) -> {
          if (plan instanceof final OldBackups old) {
            backupIds.put(
                partitionId, old.backupIds().stream().mapToLong(Long::longValue).toArray());
          }
        });
    return backupIds;
  }

  /** How one partition is restored. */
  public sealed interface PartitionPlan permits OldBackups, FromArchive {}

  /**
   * Restore from old backups with the existing restore, unchanged.
   *
   * @param backupIds the backups to restore, the last one covering the checkpoint
   */
  public record OldBackups(List<Long> backupIds) implements PartitionPlan {
    public OldBackups {
      backupIds = List.copyOf(backupIds);
    }
  }

  /**
   * Restore from the log archive.
   *
   * @param view the timeline that covers the checkpoint on this partition
   * @param checkpoint the checkpoint in that timeline
   * @param exportedPosition the position the RDBMS exported up to; the journal then starts at the
   *     next position or the snapshot, whichever comes first. Empty to start at the snapshot.
   */
  public record FromArchive(
      LogArchiveView view, CoveredCheckpointJson checkpoint, Optional<Long> exportedPosition)
      implements PartitionPlan {

    public String timelineId() {
      return view.timelineId();
    }

    @Override
    public String toString() {
      return "FromArchive[timeline=%s, checkpoint=%d, position=%d, exportedPosition=%s]"
          .formatted(
              timelineId(),
              checkpoint.checkpointId(),
              checkpoint.checkpointPosition(),
              exportedPosition.map(String::valueOf).orElse("none"));
    }
  }
}
