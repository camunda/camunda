/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector;

/**
 * The liveness of a broker as consolidated by the system partition's failure detector from what the
 * brokers observing it report.
 */
public enum MemberLivenessState {
  /** No observer has evidence about the broker yet, e.g. it was never seen. */
  UNKNOWN(0),
  /** At least one observer sees the broker. */
  HEALTHY(1),
  /** No observer sees the broker, but not for long enough or not confirmed by enough observers. */
  SUSPECT(2),
  /** Confirmed by enough observers to be out of sight for longer than the detection window. */
  DEAD(3);

  private final int code;

  MemberLivenessState(final int code) {
    this.code = code;
  }

  /** The value reported by the broker state gauge. */
  public int code() {
    return code;
  }
}
