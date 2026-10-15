/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector;

import java.util.Collection;

/**
 * The liveness of a region, aggregated from the {@link MemberLivenessState liveness} of its brokers
 * running the system partition. The code is exported as a metric.
 */
public enum RegionState {
  /** Not every broker of the region was seen yet, and none is suspect or dead. */
  UNKNOWN(0),
  /** Every broker of the region is healthy. */
  ACTIVE(1),
  /** Some broker of the region is suspect or dead, but not all of them are dead. */
  SUSPECT(2),
  /** Every broker of the region is dead. */
  FAILED(3);

  private final int code;

  RegionState(final int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  /** Returns the state of a region whose brokers are in the given states. */
  public static RegionState of(final Collection<MemberLivenessState> memberStates) {
    if (memberStates.isEmpty()) {
      return UNKNOWN;
    }
    if (memberStates.stream().allMatch(state -> state == MemberLivenessState.DEAD)) {
      return FAILED;
    }
    if (memberStates.stream()
        .anyMatch(
            state -> state == MemberLivenessState.SUSPECT || state == MemberLivenessState.DEAD)) {
      return SUSPECT;
    }
    if (memberStates.contains(MemberLivenessState.UNKNOWN)) {
      return UNKNOWN;
    }
    return ACTIVE;
  }
}
