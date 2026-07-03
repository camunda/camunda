/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.window;

/**
 * A windowing strategy: assigns an event time to the window it falls into, and carries the window
 * size and the grace period (allowed lateness). Grace is part of the window definition — not a
 * separate parameter threaded through the aggregation — mirroring Kafka Streams' {@code
 * TimeWindows.ofSizeAndGrace}. The window a aggregation finalizes and evicts once the event-time
 * watermark passes {@code windowEnd + grace}.
 */
public interface Windows {

  /** The start (inclusive) of the window {@code eventTimeMs} falls into. */
  long windowStart(long eventTimeMs);

  /** The window size in milliseconds. */
  long sizeMs();

  /** The grace period (allowed lateness) after a window's end before it finalizes and evicts. */
  long graceMs();
}
