/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Tracks the {@link MemberLivenessState liveness} of every broker from the latest committed tick
 * and the cluster view: a broker never seen is {@link MemberLivenessState#UNKNOWN unknown}, one
 * which missed more ticks than the suspect threshold is {@link MemberLivenessState#SUSPECT
 * suspect}, more than the missed ticks threshold {@link MemberLivenessState#DEAD dead}, and
 * otherwise {@link MemberLivenessState#HEALTHY healthy}. A suspect or dead broker becomes healthy
 * again as soon as it is seen. Ticks count instead of time, so nothing ages while no tick commits,
 * during a leader election or without a quorum. Logs only when a state changes.
 */
final class BrokerLivenessEvaluator {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final Set<String> members;
  private final LivenessThresholds thresholds;
  private final TickTockMetrics metrics;
  private final Map<String, MemberLivenessState> states = new HashMap<>();

  BrokerLivenessEvaluator(
      final Set<String> members,
      final LivenessThresholds thresholds,
      final TickTockMetrics metrics) {
    this.members = new TreeSet<>(members);
    this.thresholds = thresholds;
    this.metrics = metrics;
  }

  /** Evaluates every broker at the given tick and returns their states. */
  Map<String, MemberLivenessState> evaluate(
      final long tickId, final Map<String, Long> lastSeenTicks) {
    for (final var member : members) {
      final var lastSeen = lastSeenTicks.get(member);
      final var missed = LivenessThresholds.missed(tickId, lastSeen);
      final var state = thresholds.stateOf(tickId, lastSeen);
      metrics.setBrokerLiveness(member, missed, state.code());
      final var previous = states.put(member, state);
      if (state != previous) {
        logTransition(member, previous, state, missed, tickId);
      }
    }
    return Map.copyOf(states);
  }

  /** Returns the state of the broker, or null before the first evaluation. */
  @Nullable MemberLivenessState stateOf(final String member) {
    return states.get(member);
  }

  private void logTransition(
      final String member,
      final @Nullable MemberLivenessState from,
      final MemberLivenessState to,
      final long missed,
      final long tickId) {
    switch (to) {
      case DEAD ->
          LOG.warn(
              "Broker {} missed {} ticks (threshold {}); marking as dead",
              member,
              missed,
              thresholds.dead());
      case SUSPECT ->
          LOG.info(
              "Broker {} missed {} ticks (threshold {}); marking as suspect",
              member,
              missed,
              thresholds.suspect());
      case HEALTHY ->
          LOG.info(
              "Broker {} is healthy at tick {} of the system partition{}",
              member,
              tickId,
              from == null || from == MemberLivenessState.UNKNOWN ? "" : " again, was " + from);
      case UNKNOWN -> LOG.debug("Broker {} never responded up to tick {}", member, tickId);
      default -> throw new IllegalStateException("Unexpected liveness state " + to);
    }
  }
}
