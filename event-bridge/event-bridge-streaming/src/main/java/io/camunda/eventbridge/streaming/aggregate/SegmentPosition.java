/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

/**
 * A monotonic position within a source partition's sealed output: the {@code segment} index and,
 * within it, the {@code chunk} index (an oversized segment is split into ordered chunks). Ordered
 * lexicographically (segment, then chunk), so it serves as the dedup high-watermark in {@link
 * SegmentDedup}.
 */
public record SegmentPosition(long segment, int chunk) implements Comparable<SegmentPosition> {

  @Override
  public int compareTo(final SegmentPosition other) {
    final int bySegment = Long.compare(segment, other.segment);
    return bySegment != 0 ? bySegment : Integer.compare(chunk, other.chunk);
  }
}
