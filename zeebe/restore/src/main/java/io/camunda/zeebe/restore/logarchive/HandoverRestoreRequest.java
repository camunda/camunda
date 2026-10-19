/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What to restore to across the handover: explicit backup ids, or a time range (both bounds
 * optional; neither means the latest restore point).
 *
 * @param backupIds the backups to restore, the last one is the target checkpoint; empty for a time
 *     range
 * @param from the start of the time range
 * @param to the restore point is the one closest to it; the latest one if absent
 * @param exportedPositions the positions the RDBMS exported up to, by partition id; empty without
 *     RDBMS. Only used for a time range, like today's restore.
 */
public record HandoverRestoreRequest(
    List<Long> backupIds,
    @Nullable Instant from,
    @Nullable Instant to,
    Map<Integer, Long> exportedPositions) {

  public HandoverRestoreRequest {
    backupIds = backupIds.stream().sorted().toList();
    exportedPositions = Map.copyOf(exportedPositions);
    if (!backupIds.isEmpty() && (from != null || to != null)) {
      throw new IllegalArgumentException(
          "Expected either backup ids or a time range, but got both: backupIds=%s, from=%s, to=%s"
              .formatted(backupIds, from, to));
    }
  }

  public static HandoverRestoreRequest ofBackupIds(final List<Long> backupIds) {
    if (backupIds.isEmpty()) {
      throw new IllegalArgumentException("Expected at least one backup id");
    }
    return new HandoverRestoreRequest(backupIds, null, null, Map.of());
  }

  public static HandoverRestoreRequest ofTimeRange(
      final @Nullable Instant from,
      final @Nullable Instant to,
      final Map<Integer, Long> exportedPositions) {
    return new HandoverRestoreRequest(List.of(), from, to, exportedPositions);
  }

  public boolean byBackupId() {
    return !backupIds.isEmpty();
  }

  /** The checkpoint named by the backup ids. */
  public long targetBackupId() {
    return backupIds.getLast();
  }

  public @Nullable Long exportedPosition(final int partitionId) {
    return exportedPositions.get(partitionId);
  }
}
