/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Resolves the checkpoint to restore to across all partitions (spec section 13, step 2).
 *
 * <p>A checkpoint is restorable on a partition when the partition's archive covers it: it is listed
 * as a covered checkpoint and its position is at most the partition's archived position F.
 */
public final class LogArchiveRestorePointResolver {

  private LogArchiveRestorePointResolver() {}

  /**
   * @param views the views of all partitions, by partition id
   * @param checkpointId the checkpoint to restore to, or empty for the newest checkpoint that every
   *     partition covers
   * @throws LogArchiveRestoreException if the checkpoint is not covered by every partition
   */
  public static LogArchiveRestorePoint resolve(
      final Map<Integer, LogArchiveView> views, final Optional<Long> checkpointId) {
    if (views.isEmpty()) {
      throw new IllegalArgumentException("Expected views of at least one partition");
    }
    final var sorted = new TreeMap<>(views);
    return checkpointId.isPresent()
        ? resolveGiven(sorted, checkpointId.get())
        : resolveLatest(sorted);
  }

  private static LogArchiveRestorePoint resolveGiven(
      final TreeMap<Integer, LogArchiveView> views, final long checkpointId) {
    final var checkpoints = new HashMap<Integer, CoveredCheckpointJson>();
    for (final var view : views.values()) {
      final var checkpoint =
          findRestorable(view, checkpointId)
              .orElseThrow(
                  () ->
                      new LogArchiveRestoreException(
                          "Checkpoint %d is not covered by the log archive of partition %d (timeline '%s', archived position %d)"
                              .formatted(
                                  checkpointId,
                                  view.partitionId(),
                                  view.timelineId(),
                                  view.archivedPosition())));
      checkpoints.put(view.partitionId(), checkpoint);
    }
    return new LogArchiveRestorePoint(checkpointId, checkpoints);
  }

  /**
   * Any checkpoint common to all partitions is covered by the first one, so walking the first
   * partition's checkpoints from the newest and taking the first one all others cover finds the
   * newest common checkpoint.
   */
  private static LogArchiveRestorePoint resolveLatest(
      final TreeMap<Integer, LogArchiveView> views) {
    final var reference = views.firstEntry().getValue();
    return reference
        .checkpointsNewestFirst()
        .filter(candidate -> candidate.checkpointPosition() <= reference.archivedPosition())
        .map(candidate -> tryResolve(views, candidate.checkpointId()))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseThrow(
            () ->
                new LogArchiveRestoreException(
                    "No checkpoint is covered by the log archives of all partitions %s"
                        .formatted(views.keySet())));
  }

  private static Optional<LogArchiveRestorePoint> tryResolve(
      final TreeMap<Integer, LogArchiveView> views, final long checkpointId) {
    final var checkpoints = new HashMap<Integer, CoveredCheckpointJson>();
    for (final var view : views.values()) {
      final var checkpoint = findRestorable(view, checkpointId);
      if (checkpoint.isEmpty()) {
        return Optional.empty();
      }
      checkpoints.put(view.partitionId(), checkpoint.get());
    }
    return Optional.of(new LogArchiveRestorePoint(checkpointId, checkpoints));
  }

  private static Optional<CoveredCheckpointJson> findRestorable(
      final LogArchiveView view, final long checkpointId) {
    return view.findCheckpoint(checkpointId)
        .filter(checkpoint -> checkpoint.checkpointPosition() <= view.archivedPosition());
  }
}
