/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.window;

/**
 * Fixed-size, non-overlapping (tumbling) event-time windows. Assigns an event timestamp to the
 * start of the window it falls into; that window start becomes part of the grouping key.
 */
public final class TumblingWindows implements Windows {

  private final long sizeMs;
  private final long graceMs;

  private TumblingWindows(final long sizeMs, final long graceMs) {
    if (sizeMs <= 0) {
      throw new IllegalArgumentException("window size must be positive, was " + sizeMs);
    }
    if (graceMs < 0) {
      throw new IllegalArgumentException("grace must be non-negative, was " + graceMs);
    }
    this.sizeMs = sizeMs;
    this.graceMs = graceMs;
  }

  /** Windows of {@code sizeMs} milliseconds with no grace (allowed lateness {@code 0}). */
  public static TumblingWindows of(final long sizeMs) {
    return new TumblingWindows(sizeMs, 0);
  }

  /** Windows of {@code sizeMs} that finalize {@code graceMs} after the window end. */
  public static TumblingWindows ofSizeAndGrace(final long sizeMs, final long graceMs) {
    return new TumblingWindows(sizeMs, graceMs);
  }

  /** The start (inclusive) of the window {@code eventTimeMs} falls into. */
  @Override
  public long windowStart(final long eventTimeMs) {
    return Math.floorDiv(eventTimeMs, sizeMs) * sizeMs;
  }

  @Override
  public long sizeMs() {
    return sizeMs;
  }

  @Override
  public long graceMs() {
    return graceMs;
  }
}
