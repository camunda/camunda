/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.model.ArchivedSnapshotJson;
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import java.util.List;
import java.util.Optional;

/**
 * What to download to restore one partition to a checkpoint.
 *
 * @param partitionId the partition
 * @param checkpoint the checkpoint on this partition
 * @param snapshot the snapshot to install, empty if the objects start at index 1
 * @param objects the contiguous objects to replay, from the one covering the snapshot index up to
 *     the one containing the checkpoint position
 */
public record LogArchiveRestorePlan(
    int partitionId,
    CoveredCheckpointJson checkpoint,
    Optional<ArchivedSnapshotJson> snapshot,
    List<LogObjectJson> objects) {

  public LogArchiveRestorePlan {
    objects = List.copyOf(objects);
  }

  /** The index the snapshot covers, 0 without a snapshot. */
  public long snapshotIndex() {
    return snapshot.map(ArchivedSnapshotJson::index).orElse(0L);
  }

  /** The first index of the rebuilt journal. */
  public long firstIndex() {
    return objects.getFirst().firstIndex();
  }
}
