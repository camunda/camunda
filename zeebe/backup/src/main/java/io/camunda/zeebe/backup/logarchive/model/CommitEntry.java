/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A numbered commit entry, written as {@code commits/<seq>.json} after the publisher has committed
 * the event with that seq (spec section 9.2).
 *
 * <p>Optional parts are omitted from the JSON when they do not apply, so the reader can tell "not
 * applicable" from a value. The rule is per field:
 *
 * <ul>
 *   <li>{@code startIndex}, {@code parentTimelineId}, {@code snapshot}, {@code chunk}: omitted when
 *       {@code null}. The publisher sets them only for the matching {@link Type}.
 *   <li>{@code objects}, {@code checkpoints}: omitted when empty. An absent list is read back as an
 *       empty list, so the record never holds {@code null} lists.
 * </ul>
 *
 * @param recordPosition position of the event record that this entry describes
 * @param recordTimestamp timestamp of the event record that this entry describes, in epoch millis
 * @param startIndex only for {@link Type#TIMELINE_STARTED}
 * @param parentTimelineId only for {@link Type#TIMELINE_STARTED}
 * @param objects only for {@link Type#LOG_ARCHIVED}
 * @param checkpoints only for {@link Type#LOG_ARCHIVED}
 * @param snapshot only for {@link Type#SNAPSHOT_ARCHIVED}
 * @param chunk only for {@link Type#INDEX_SEALED}
 */
@JsonPropertyOrder({
  "formatVersion",
  "timelineId",
  "partitionId",
  "seq",
  "type",
  "recordPosition",
  "recordTimestamp",
  "archivedIndex",
  "archivedPosition",
  "startIndex",
  "parentTimelineId",
  "objects",
  "checkpoints",
  "snapshot",
  "chunk"
})
public record CommitEntry(
    int formatVersion,
    String timelineId,
    int partitionId,
    long seq,
    Type type,
    long recordPosition,
    long recordTimestamp,
    long archivedIndex,
    long archivedPosition,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Long startIndex,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String parentTimelineId,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<LogObjectJson> objects,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CoveredCheckpointJson> checkpoints,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable ArchivedSnapshotJson snapshot,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable IndexChunkJson chunk) {

  public CommitEntry {
    objects = objects == null ? List.of() : List.copyOf(objects);
    checkpoints = checkpoints == null ? List.of() : List.copyOf(checkpoints);
  }

  /** The event type a commit entry describes. Serialized by name. */
  public enum Type {
    TIMELINE_STARTED,
    LOG_ARCHIVED,
    SNAPSHOT_ARCHIVED,
    INDEX_SEALED
  }
}
