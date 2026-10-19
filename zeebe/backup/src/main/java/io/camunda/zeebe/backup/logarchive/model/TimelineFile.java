/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 */
@JsonPropertyOrder({
  "formatVersion",
  "timelineId",
  "partitionId",
  "parentTimelineId",
  "startIndex",
  "startedSeq"
})
public record TimelineFile(
    int formatVersion,
    String timelineId,
    int partitionId,
    String parentTimelineId,
    long startIndex,
    long startedSeq) {}
