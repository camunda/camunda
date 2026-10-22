/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidFooter;

/** Who wrote a segment, and so how its body is cut into frames. */
public enum Level {
  /** Uploaded by a publisher: a single frame. */
  PUBLISHER(0, Long.MAX_VALUE),
  /** Written by the Event Plane from a day's segments: frames of about 32 MiB of entries. */
  ROLLUP(1, 32L << 20);

  private final int code;
  private final long frameTarget;

  Level(int code, long frameTarget) {
    this.code = code;
    this.frameTarget = frameTarget;
  }

  int code() {
    return code;
  }

  /** The entries' bytes after which a writer ends a frame. */
  long frameTarget() {
    return frameTarget;
  }

  static Level of(int code) {
    for (var level : values()) {
      if (level.code == code) {
        return level;
      }
    }
    throw new InvalidFooter.UnknownLevel(code);
  }
}
