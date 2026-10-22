/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

/** What a broker's own SWIM membership view says about another broker. */
enum ObservedState {
  /**
   * Never seen alive by the observer, so the observer has no evidence either way: it abstains
   * instead of claiming the broker is gone, which it would for every broker still starting.
   */
  UNKNOWN((byte) 0),
  /** SWIM lists the broker as reachable. */
  ALIVE((byte) 1),
  /** SWIM lists the broker, but it did not answer probes. SWIM may refute this on its own. */
  SUSPECT((byte) 2),
  /** SWIM removed the broker after it stayed suspect for its failure timeout. */
  DEAD((byte) 3);

  private final byte code;

  ObservedState(final byte code) {
    this.code = code;
  }

  byte code() {
    return code;
  }

  boolean isOutOfSight() {
    return this == SUSPECT || this == DEAD;
  }

  static ObservedState of(final byte code) {
    for (final var state : values()) {
      if (state.code == code) {
        return state;
      }
    }
    throw new IllegalArgumentException("Expected a known observed state code, but got " + code);
  }
}
