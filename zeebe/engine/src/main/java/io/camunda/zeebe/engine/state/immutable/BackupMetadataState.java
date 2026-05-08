/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.backupmetadata.BackupMetadataRecord;
import java.util.function.Consumer;

/**
 * Read-only access to the backup-metadata state stored on the system partition.
 *
 * <p>Rows are keyed by {@code (checkpointId, partitionId)} and carry the per-partition status of a
 * backup checkpoint as observed by the cluster coordinator.
 */
public interface BackupMetadataState {

  /** Returns the row for {@code (checkpointId, partitionId)}, or {@code null} if absent. */
  BackupMetadataRecord get(long checkpointId, int partitionId);

  /**
   * Iterates over every row whose checkpoint id equals {@code checkpointId}, invoking {@code
   * consumer} with a fresh {@link BackupMetadataRecord} per row.
   */
  void iterateByCheckpoint(long checkpointId, Consumer<BackupMetadataRecord> consumer);

  /** Iterates over every persisted row. */
  void iterateAll(Consumer<BackupMetadataRecord> consumer);
}
