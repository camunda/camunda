/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

/**
 * What an observer sees of one broker.
 *
 * @param memberId the observed broker
 * @param state the observer's SWIM view of it
 * @param unreachableMillis for how long the observer has continuously not seen the broker, by the
 *     observer's own monotonic clock so that clock skew between brokers cannot distort it; 0 unless
 *     the broker is out of sight
 */
record Observation(String memberId, ObservedState state, long unreachableMillis) {

  static Observation unknown(final String memberId) {
    return new Observation(memberId, ObservedState.UNKNOWN, 0);
  }

  static Observation alive(final String memberId) {
    return new Observation(memberId, ObservedState.ALIVE, 0);
  }

  static Observation suspect(final String memberId, final long unreachableMillis) {
    return new Observation(memberId, ObservedState.SUSPECT, unreachableMillis);
  }

  static Observation dead(final String memberId, final long unreachableMillis) {
    return new Observation(memberId, ObservedState.DEAD, unreachableMillis);
  }
}
