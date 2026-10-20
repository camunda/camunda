/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import java.util.UUID;

/**
 * The fixed-size footer of a segment (version 1, 66 bytes).
 *
 * @param history the segment's history, a name-based UUID of the timeline
 * @param firstIndex index of the first entry
 * @param lastIndex index of the last entry
 * @param firstPosition first record position, -1 if the segment holds no records
 * @param lastPosition last record position, -1 if the segment holds no records
 * @param latestTimestamp highest record timestamp in epoch millis, -1 if the segment holds no
 *     records
 * @param bodyLength length of the body, which is also the offset at which the metadata starts
 * @param level 0 if a publisher uploaded the segment, 1 for a rollup
 */
public record SegmentFooter(
    UUID history,
    long firstIndex,
    long lastIndex,
    long firstPosition,
    long lastPosition,
    long latestTimestamp,
    long bodyLength,
    int level) {

  public int entryCount() {
    return (int) (lastIndex - firstIndex + 1);
  }
}
