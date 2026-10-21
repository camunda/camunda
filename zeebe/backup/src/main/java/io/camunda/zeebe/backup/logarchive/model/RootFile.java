/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The content of {@code root/<seq>.json} (spec section 9.4). Every list is always written, even
 * when empty, and lists are in the order given by the spec: chunks and snapshots by their order,
 * open and merged objects by first index.
 *
 * @param parentTimelineId empty for a timeline that was not started from another one
 * @param mergedIndex M, the highest merged index; {@code startIndex - 1} before the first merge
 * @param mergedPosition the highest position of a merged object, -1 if none
 * @param releasedIndex R, the highest released index; {@code startIndex - 1} before the first
 *     release
 * @param releasedPosition the highest position of a released object, -1 if none
 * @param holdIndex H, the restore hold of a timeline in mode B; null (omitted) in mode {@code
 *     CLUSTER}
 * @param compactionMode {@code AUTHORITY} for a timeline in mode B; null (omitted) for {@code
 *     CLUSTER}
 * @param chunks all sealed index chunks, in chunk order
 * @param openObjects all open objects, in first index order
 * @param openCheckpoints covered checkpoints newer than the last sealed chunk
 * @param mergedObjects all merged objects above the released index, in first index order
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
  "mergedIndex",
  "mergedPosition",
  "releasedIndex",
  "releasedPosition",
  "holdIndex",
  "compactionMode",
  "chunks",
  "openObjects",
  "openCheckpoints",
  "mergedObjects",
  "snapshots",
  "handover"
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
    long mergedIndex,
    long mergedPosition,
    long releasedIndex,
    long releasedPosition,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Long holdIndex,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String compactionMode,
    List<IndexChunkJson> chunks,
    List<LogObjectJson> openObjects,
    List<CoveredCheckpointJson> openCheckpoints,
    List<LogObjectJson> mergedObjects,
    List<ArchivedSnapshotJson> snapshots,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable HandoverJson handover) {

  /** A root of a timeline in mode {@code CLUSTER}. */
  public RootFile(
      final int formatVersion,
      final String timelineId,
      final String parentTimelineId,
      final int partitionId,
      final long seq,
      final long startIndex,
      final long archivedIndex,
      final long archivedPosition,
      final long mergedIndex,
      final long mergedPosition,
      final long releasedIndex,
      final long releasedPosition,
      final List<IndexChunkJson> chunks,
      final List<LogObjectJson> openObjects,
      final List<CoveredCheckpointJson> openCheckpoints,
      final List<LogObjectJson> mergedObjects,
      final List<ArchivedSnapshotJson> snapshots,
      final @Nullable HandoverJson handover) {
    this(
        formatVersion,
        timelineId,
        parentTimelineId,
        partitionId,
        seq,
        startIndex,
        archivedIndex,
        archivedPosition,
        mergedIndex,
        mergedPosition,
        releasedIndex,
        releasedPosition,
        null,
        null,
        chunks,
        openObjects,
        openCheckpoints,
        mergedObjects,
        snapshots,
        handover);
  }

  /** A root of a timeline that has neither merged nor released anything, without a handover. */
  public RootFile(
      final int formatVersion,
      final String timelineId,
      final String parentTimelineId,
      final int partitionId,
      final long seq,
      final long startIndex,
      final long archivedIndex,
      final long archivedPosition,
      final List<IndexChunkJson> chunks,
      final List<LogObjectJson> openObjects,
      final List<CoveredCheckpointJson> openCheckpoints,
      final List<ArchivedSnapshotJson> snapshots) {
    this(
        formatVersion,
        timelineId,
        parentTimelineId,
        partitionId,
        seq,
        startIndex,
        archivedIndex,
        archivedPosition,
        chunks,
        openObjects,
        openCheckpoints,
        snapshots,
        null);
  }

  /** A root of a timeline that has neither merged nor released anything. */
  public RootFile(
      final int formatVersion,
      final String timelineId,
      final String parentTimelineId,
      final int partitionId,
      final long seq,
      final long startIndex,
      final long archivedIndex,
      final long archivedPosition,
      final List<IndexChunkJson> chunks,
      final List<LogObjectJson> openObjects,
      final List<CoveredCheckpointJson> openCheckpoints,
      final List<ArchivedSnapshotJson> snapshots,
      final @Nullable HandoverJson handover) {
    this(
        formatVersion,
        timelineId,
        parentTimelineId,
        partitionId,
        seq,
        startIndex,
        archivedIndex,
        archivedPosition,
        startIndex - 1,
        -1,
        startIndex - 1,
        -1,
        chunks,
        openObjects,
        openCheckpoints,
        List.of(),
        snapshots,
        handover);
  }

  /**
   * Reads a root, defaulting the fields that older roots do not have to a timeline that has neither
   * merged nor released anything. Without this, Jackson would default a missing primitive to 0,
   * which is a valid index.
   */
  @JsonCreator
  static RootFile fromJson(
      @JsonProperty("formatVersion") final int formatVersion,
      @JsonProperty("timelineId") final String timelineId,
      @JsonProperty("parentTimelineId") final String parentTimelineId,
      @JsonProperty("partitionId") final int partitionId,
      @JsonProperty("seq") final long seq,
      @JsonProperty("startIndex") final long startIndex,
      @JsonProperty("archivedIndex") final long archivedIndex,
      @JsonProperty("archivedPosition") final long archivedPosition,
      @JsonProperty("mergedIndex") final @Nullable Long mergedIndex,
      @JsonProperty("mergedPosition") final @Nullable Long mergedPosition,
      @JsonProperty("releasedIndex") final @Nullable Long releasedIndex,
      @JsonProperty("releasedPosition") final @Nullable Long releasedPosition,
      @JsonProperty("holdIndex") final @Nullable Long holdIndex,
      @JsonProperty("compactionMode") final @Nullable String compactionMode,
      @JsonProperty("chunks") final List<IndexChunkJson> chunks,
      @JsonProperty("openObjects") final List<LogObjectJson> openObjects,
      @JsonProperty("openCheckpoints") final List<CoveredCheckpointJson> openCheckpoints,
      @JsonProperty("mergedObjects") final @Nullable List<LogObjectJson> mergedObjects,
      @JsonProperty("snapshots") final List<ArchivedSnapshotJson> snapshots,
      @JsonProperty("handover") final @Nullable HandoverJson handover) {
    return new RootFile(
        formatVersion,
        timelineId,
        parentTimelineId,
        partitionId,
        seq,
        startIndex,
        archivedIndex,
        archivedPosition,
        mergedIndex == null ? startIndex - 1 : mergedIndex,
        mergedPosition == null ? -1L : mergedPosition,
        releasedIndex == null ? startIndex - 1 : releasedIndex,
        releasedPosition == null ? -1L : releasedPosition,
        holdIndex,
        compactionMode,
        chunks,
        openObjects,
        openCheckpoints,
        mergedObjects == null ? List.of() : mergedObjects,
        snapshots,
        handover);
  }

  public RootFile {
    compactionMode = CompactionModes.normalize(compactionMode);
    chunks = chunks == null ? List.of() : List.copyOf(chunks);
    openObjects = openObjects == null ? List.of() : List.copyOf(openObjects);
    openCheckpoints = openCheckpoints == null ? List.of() : List.copyOf(openCheckpoints);
    mergedObjects = mergedObjects == null ? List.of() : List.copyOf(mergedObjects);
    snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
  }

  /** Whether the timeline is in mode B. A missing mode is {@code CLUSTER}. */
  @JsonIgnore
  public boolean isAuthority() {
    return CompactionModes.isAuthority(compactionMode);
  }
}
