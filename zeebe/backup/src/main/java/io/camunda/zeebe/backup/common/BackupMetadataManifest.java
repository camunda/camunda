/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.List;

/**
 * Per-partition JSON manifest containing checkpoint metadata and pre-computed backup ranges. Synced
 * to the backup store on every mutation (backup confirmation, deletion). A single file is
 * maintained per partition and overwritten on each sync. If the file is found to be corrupted on
 * read, it is re-synced from the authoritative RocksDB column families.
 */
public record BackupMetadataManifest(
    @JsonProperty("partitionId") int partitionId,
    @JsonProperty("lastUpdated") Instant lastUpdated,
    @JsonProperty("checkpoints") List<CheckpointEntry> checkpoints,
    @JsonProperty("ranges") List<RangeEntry> ranges) {

  @JsonCreator
  public BackupMetadataManifest {}

  /** A single checkpoint entry with full metadata. */
  public record CheckpointEntry(
      @JsonProperty("checkpointId") long checkpointId,
      @JsonProperty("checkpointPosition") long checkpointPosition,
      @JsonProperty("checkpointTimestamp") Instant checkpointTimestamp,
      @JsonProperty("checkpointType") CheckpointType checkpointType,
      @JsonProperty("firstLogPosition") long firstLogPosition,
      @JsonProperty("numberOfPartitions") int numberOfPartitions,
      @JsonProperty("brokerVersion") String brokerVersion) {

    @JsonCreator
    public CheckpointEntry {}
  }

  /** A contiguous backup range [start, end]. */
  public record RangeEntry(@JsonProperty("start") long start, @JsonProperty("end") long end) {

    @JsonCreator
    public RangeEntry {}
  }
}
