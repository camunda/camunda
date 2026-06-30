/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.window;

/**
 * Fixed-size, non-overlapping (tumbling) event-time windows. Assigns an event timestamp to the
 * start of the window it falls into; that window start becomes part of the grouping key.
 */
public final class TumblingWindows {

  private final long sizeMs;

  private TumblingWindows(final long sizeMs) {
    if (sizeMs <= 0) {
      throw new IllegalArgumentException("window size must be positive, was " + sizeMs);
    }
    this.sizeMs = sizeMs;
  }

  /** Windows of {@code sizeMs} milliseconds. */
  public static TumblingWindows of(final long sizeMs) {
    return new TumblingWindows(sizeMs);
  }

  /** The start (inclusive) of the window {@code eventTimeMs} falls into. */
  public long windowStart(final long eventTimeMs) {
    return Math.floorDiv(eventTimeMs, sizeMs) * sizeMs;
  }

  public long sizeMs() {
    return sizeMs;
  }
}
