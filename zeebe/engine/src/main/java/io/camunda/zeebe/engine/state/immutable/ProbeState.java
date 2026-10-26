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

public interface ProbeState {

  /**
   * Returns a copy of the probe, or {@code null} if no probe with this key exists (never created,
   * or already ended).
   */
  ProbeRecord getProbe(long probeKey);

  /** Returns the state of the probe, or {@link State#NOT_FOUND} if it does not exist. */
  State getState(long probeKey);

  /**
   * Visits the probes whose deadline is at or before the timestamp, earliest first, until the
   * visitor returns {@code false}.
   *
   * @return the deadline of the first probe not visited, or {@code -1} if all were visited
   */
  long findTimedOutProbes(long timestamp, BiPredicate<Long, ProbeRecord> visitor);

  enum State {
    /** Created, or yielded and about to be dispatched again. */
    CREATED,
    /** Waiting for the resolution of a secret it references. */
    PARKED,
    /** Pushed to a worker, waiting for its answer. */
    ACTIVATED,
    NOT_FOUND
  }
}
