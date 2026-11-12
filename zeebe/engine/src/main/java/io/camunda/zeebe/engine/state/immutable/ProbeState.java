/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import java.util.function.BiPredicate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface ProbeState {

  /**
   * Returns a copy of the probe, or {@code null} if no probe with this key exists: never created,
   * or already ended.
   */
  @Nullable ProbeRecord getProbe(long probeKey);

  /** Returns the state of the probe, or {@link State#NOT_FOUND} if it does not exist. */
  State getState(long probeKey);

  /**
   * Visits copies of the probes whose deadline is at or before the timestamp, earliest first, until
   * the visitor returns {@code false}.
   *
   * @return the deadline of the first probe not visited, or {@code -1} if no probe is left
   */
  long forEachTimedOutProbe(long timestamp, BiPredicate<Long, ProbeRecord> visitor);

  enum State {
    /** Waiting for a poll of its probe ID to collect it. */
    ACTIVATABLE,
    /** Collected by a poll, but a secret it references was not cached: waiting for it. */
    WAITING_FOR_SECRET_RESOLUTION,
    /** Handed to a worker, waiting for its answer. */
    ACTIVATED,
    NOT_FOUND
  }
}
