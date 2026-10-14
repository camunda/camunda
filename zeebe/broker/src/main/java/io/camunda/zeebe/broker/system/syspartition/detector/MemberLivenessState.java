/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector;

/**
 * The liveness of a broker as seen through the ticks committed to the system partition. It is a
 * function of the committed cluster view only, so every broker derives the same state.
 */
public enum MemberLivenessState {
  /** Never seen at any tick. */
  UNKNOWN(0),
  /** Seen within the suspect threshold of ticks. */
  HEALTHY(1),
  /** Missed more ticks than the suspect threshold; may be transient. */
  SUSPECT(2),
  /** Missed more ticks than the missed ticks threshold; considered gone. */
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
