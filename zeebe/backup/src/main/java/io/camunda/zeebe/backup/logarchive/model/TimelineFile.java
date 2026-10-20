/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * The registry file {@code <timeline>/p<partition>/timeline.json} (spec section 9.6). It is written
 * once, when the timeline starts, and lets restore find the timelines and their parents without
 * listing their data. It carries what the {@code TIMELINE_STARTED} commit entry carries, so the
 * same timeline always yields the same bytes.
 *
 * @param timelineId the timeline this file belongs to
 * @param partitionId the partition this file belongs to
 * @param parentTimelineId the timeline this one continues, empty if none
 * @param startIndex the first Raft index of the timeline
 * @param startedSeq the seq of the {@code TIMELINE_STARTED} commit entry
 * @param startedAt the record timestamp of the {@code TIMELINE_STARTED} event in epoch millis, -1
 *     if unknown (files written before the field existed)
 * @param parentIndex the Raft index of the last entry shared with the parent, -1 if unknown or no
 *     parent (also for files written before the field existed)
 * @param parentPosition the position of the last record shared with the parent, -1 if unknown or no
 *     parent (also for files written before the field existed)
 */
@JsonPropertyOrder({
  "formatVersion",
  "timelineId",
  "partitionId",
  "parentTimelineId",
  "startIndex",
  "startedSeq",
  "startedAt",
  "parentIndex",
  "parentPosition"
})
public record TimelineFile(
    int formatVersion,
    String timelineId,
    int partitionId,
    String parentTimelineId,
    long startIndex,
    long startedSeq,
    long startedAt,
    long parentIndex,
    long parentPosition) {

  /**
   * Reads a file, defaulting the fields that older files do not have. Without this, Jackson would
   * default a missing primitive to 0, which is a valid index.
   */
  @JsonCreator
  static TimelineFile fromJson(
      @JsonProperty("formatVersion") final int formatVersion,
      @JsonProperty("timelineId") final String timelineId,
      @JsonProperty("partitionId") final int partitionId,
      @JsonProperty("parentTimelineId") final String parentTimelineId,
      @JsonProperty("startIndex") final long startIndex,
      @JsonProperty("startedSeq") final long startedSeq,
      @JsonProperty("startedAt") final @Nullable Long startedAt,
      @JsonProperty("parentIndex") final @Nullable Long parentIndex,
      @JsonProperty("parentPosition") final @Nullable Long parentPosition) {
    return new TimelineFile(
        formatVersion,
        timelineId,
        partitionId,
        parentTimelineId,
        startIndex,
        startedSeq,
        startedAt == null ? -1L : startedAt,
        parentIndex == null ? -1L : parentIndex,
        parentPosition == null ? -1L : parentPosition);
  }
}
