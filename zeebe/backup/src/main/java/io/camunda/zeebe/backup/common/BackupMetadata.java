/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.List;

/**
 * Per-partition JSON manifest containing checkpoint metadata and pre-computed backup ranges. Synced
 * to the backup store on every mutation (backup confirmation, deletion).
 */
public record BackupMetadata(
    @JsonProperty("version") int version,
    @JsonProperty("partitionId") int partitionId,
    @JsonProperty("lastUpdated") Instant lastUpdated,
    @JsonProperty("checkpoints") List<CheckpointEntry> checkpoints,
    @JsonProperty("ranges") List<RangeEntry> ranges) {

  public static final int VERSION = 1;

  /** A single checkpoint entry with full metadata. */
  public record CheckpointEntry(
      @JsonProperty("checkpointId") long checkpointId,
      @JsonProperty("checkpointPosition") long checkpointPosition,
      @JsonProperty("checkpointTimestamp") Instant checkpointTimestamp,
      @JsonProperty("checkpointType") CheckpointType checkpointType,
      @JsonProperty("firstLogPosition") long firstLogPosition) {}

  /** A contiguous backup range [start, end]. */
  public record RangeEntry(@JsonProperty("start") long start, @JsonProperty("end") long end) {}
}
