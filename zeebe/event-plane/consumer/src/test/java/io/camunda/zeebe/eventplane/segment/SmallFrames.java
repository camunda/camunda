/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Writes rollups with frames far smaller than 32 MiB, so that tests can read from a frame in the
 * middle; the segment module keeps the frame target to itself.
 */
public final class SmallFrames {

  private SmallFrames() {}

  public static SegmentWriter writer(
      final OutputStream out,
      final Identity identity,
      final UUID history,
      final Level level,
      final long frameTarget)
      throws IOException {
    return new SegmentWriter(out, identity, history, level, frameTarget);
  }
}
