/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

/**
 * Slices a source partition's monotonic position space into fixed-size <em>segments</em> of {@code
 * stride} positions. A segment is the unit of sealed, immutable pre-aggregation in {@link
 * SegmentSealingAggregation}: segment {@code s} covers positions {@code [s*stride, (s+1)*stride)}.
 * Deterministic — the segment a position falls into is a pure function of the position, independent
 * of arrival timing — which is what makes a sealed segment's delta reproducible on replay.
 * Analogous to {@link io.camunda.eventbridge.streaming.window.Windows} but over source position,
 * not event time.
 */
public final class Segments {

  private final long stride;

  private Segments(final long stride) {
    if (stride <= 0) {
      throw new IllegalArgumentException("segment stride must be positive, was " + stride);
    }
    this.stride = stride;
  }

  public static Segments ofStride(final long stride) {
    return new Segments(stride);
  }

  /** The segment index {@code position} falls into. */
  public long index(final long position) {
    return Math.floorDiv(position, stride);
  }

  public long stride() {
    return stride;
  }

  /** The first (inclusive) position of segment {@code index}. */
  public long startPosition(final long index) {
    return index * stride;
  }
}
