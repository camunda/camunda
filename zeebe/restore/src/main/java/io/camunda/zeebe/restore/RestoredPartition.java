/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.history.HistoryManifest;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Provenance and exact journal boundary recovered from a partition's backup chain. */
public record RestoredPartition(
    int partitionId, Optional<UUID> sourceHistoryId, long index, long position) {
  public RestoredPartition {
    Objects.requireNonNull(sourceHistoryId);
    if (partitionId <= 0 || index <= 0 || position <= 0) {
      throw new IllegalArgumentException("A restored partition needs a positive checkpoint");
    }
  }

  /** Legacy backups remain restorable, but cannot silently claim known Event Plane ancestry. */
  public HistoryManifest.Base historyBase() {
    return new HistoryManifest.Base(
        sourceHistoryId.orElseThrow(
            () -> new IllegalStateException("Backup has no history identity; ancestry is unknown")),
        index,
        position);
  }
}
