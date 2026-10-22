/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.util.UUID;

/**
 * What the name of a segment says: the history whose journal it holds, the Raft indexes of its
 * first and last entries, its level, and its ULID. See {@link ObjectNames#segment}.
 *
 * @param history the history whose journal the segment holds
 * @param firstIndex the index of its first entry
 * @param lastIndex the index of its last entry, at or after the first
 * @param level 0 for a segment a publisher uploaded, 1 for a rollup of such segments
 * @param ulid tells apart segments of the same indexes, such as two rollups of the same segments
 */
public record SegmentName(UUID history, long firstIndex, long lastIndex, int level, String ulid) {

  public SegmentName {
    if (firstIndex < 0 || lastIndex < firstIndex) {
      throw new InvalidObjectNameException(
          "a first index of 0 or more, and a last index at or after it",
          firstIndex + " and " + lastIndex);
    }
    if (level < 0) {
      throw new InvalidObjectNameException("a level of 0 or more", String.valueOf(level));
    }
    if (!Ulid.isValid(ulid)) {
      throw new InvalidObjectNameException("a ULID", ulid);
    }
  }
}
