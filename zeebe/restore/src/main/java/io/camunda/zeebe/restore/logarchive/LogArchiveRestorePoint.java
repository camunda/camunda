/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import java.util.Map;

/**
 * The checkpoint all partitions are restored to, with its position on each partition.
 *
 * @param checkpointId the id shared by all partitions
 * @param checkpoints the covered checkpoint on each partition, by partition id
 */
public record LogArchiveRestorePoint(
    long checkpointId, Map<Integer, CoveredCheckpointJson> checkpoints) {

  public LogArchiveRestorePoint {
    checkpoints = Map.copyOf(checkpoints);
  }

  /** The checkpoint on the given partition. */
  public CoveredCheckpointJson checkpoint(final int partitionId) {
    final var checkpoint = checkpoints.get(partitionId);
    if (checkpoint == null) {
      throw new LogArchiveRestoreException(
          "Restore point %d has no checkpoint for partition %d"
              .formatted(checkpointId, partitionId));
    }
    return checkpoint;
  }
}
