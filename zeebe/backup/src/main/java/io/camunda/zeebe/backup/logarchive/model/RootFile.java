/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * The content of {@code root/<seq>.json} (spec section 9.4). Every list is always written, even
 * when empty, and lists are in the order given by the spec: chunks and snapshots by their order,
 * open objects by first index.
 *
 * @param parentTimelineId empty for a timeline that was not started from another one
 * @param chunks all sealed index chunks, in chunk order
 * @param openObjects all open objects, in first index order
 * @param openCheckpoints covered checkpoints newer than the last sealed chunk
 * @param snapshots all archived snapshots, in seq order
 */
@JsonPropertyOrder({
  "formatVersion",
  "timelineId",
  "parentTimelineId",
  "partitionId",
  "seq",
  "startIndex",
  "archivedIndex",
  "archivedPosition",
  "chunks",
  "openObjects",
  "openCheckpoints",
  "snapshots"
})
public record RootFile(
    int formatVersion,
    String timelineId,
    String parentTimelineId,
    int partitionId,
    long seq,
    long startIndex,
    long archivedIndex,
    long archivedPosition,
    List<IndexChunkJson> chunks,
    List<LogObjectJson> openObjects,
    List<CoveredCheckpointJson> openCheckpoints,
    List<ArchivedSnapshotJson> snapshots) {

  public RootFile {
    chunks = chunks == null ? List.of() : List.copyOf(chunks);
    openObjects = openObjects == null ? List.of() : List.copyOf(openObjects);
    openCheckpoints = openCheckpoints == null ? List.of() : List.copyOf(openCheckpoints);
    snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
  }
}
