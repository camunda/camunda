/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.processor;

/** Which clock a scheduled {@link Punctuator} advances on. */
public enum PunctuationType {

  /**
   * Fires on the wall-clock, whether or not records are flowing — for freshness (emit partials,
   * time out idle keys). Cadence is bounded by how often the runtime pumps the topology.
   */
  WALL_CLOCK_TIME,

  /**
   * Fires as event-time advances, driven by the timestamps of processed records — for
   * deterministic, replay-stable work (finalize closed windows, prune expired state). Does not
   * advance while the stream is idle.
   */
  STREAM_TIME
}
