/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import org.jspecify.annotations.Nullable;

/**
 * Derives a broker's {@link MemberLivenessState liveness} from a committed tick and the tick it was
 * last seen at. Every broker, and the leader deciding a failover, applies the same function to the
 * same committed values, so they all derive the same states.
 *
 * @param suspect the missed ticks above which a broker is suspect
 * @param dead the missed ticks above which a broker is dead
 */
record LivenessThresholds(long suspect, long dead) {

  /** Returns how many ticks up to {@code tickId} a broker missed; all of them if never seen. */
  static long missed(final long tickId, final @Nullable Long lastSeen) {
    return lastSeen == null ? tickId : Math.max(0, tickId - lastSeen);
  }

  MemberLivenessState stateOf(final long tickId, final @Nullable Long lastSeen) {
    if (lastSeen == null) {
      return MemberLivenessState.UNKNOWN;
    }
    final var missed = missed(tickId, lastSeen);
    if (missed > dead) {
      return MemberLivenessState.DEAD;
    }
    if (missed > suspect) {
      return MemberLivenessState.SUSPECT;
    }
    return MemberLivenessState.HEALTHY;
  }
}
