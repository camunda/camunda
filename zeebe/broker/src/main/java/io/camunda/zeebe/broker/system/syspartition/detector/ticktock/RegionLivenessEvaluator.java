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
import io.camunda.zeebe.broker.system.syspartition.detector.RegionLayout;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionState;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Tracks the {@link RegionState state} of every region from the liveness of its brokers running the
 * system partition: active while all are healthy, suspect once any is suspect or dead, failed once
 * all are dead. Logs only when a state changes.
 */
final class RegionLivenessEvaluator {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final RegionLayout layout;
  private final TickTockMetrics metrics;
  private final Map<String, RegionState> states = new HashMap<>();

  RegionLivenessEvaluator(final RegionLayout layout, final TickTockMetrics metrics) {
    this.layout = layout;
    this.metrics = metrics;
  }

  void evaluate(final long tickId, final Map<String, MemberLivenessState> memberStates) {
    layout
        .regionStates(member -> memberStates.getOrDefault(member, MemberLivenessState.UNKNOWN))
        .forEach(
            (region, state) -> {
              metrics.setRegionState(region, state.code());
              final var previous = states.put(region, state);
              if (state != previous) {
                logTransition(region, previous, state, tickId);
              }
            });
  }

  /** Returns the state of the region, or null before the first evaluation. */
  @Nullable RegionState stateOf(final String region) {
    return states.get(region);
  }

  private void logTransition(
      final String region,
      final @Nullable RegionState from,
      final RegionState to,
      final long tickId) {
    final var kind = layout.isDataRegion(region) ? "Data region" : "Control region";
    switch (to) {
      case FAILED ->
          LOG.warn(
              "{} {} failed at tick {} of the system partition: all of its brokers are dead",
              kind,
              region,
              tickId);
      case SUSPECT ->
          LOG.info(
              "{} {} is suspect at tick {} of the system partition: some of its brokers are"
                  + " suspect or dead",
              kind,
              region,
              tickId);
      case ACTIVE ->
          LOG.info(
              "{} {} is active at tick {} of the system partition{}",
              kind,
              region,
              tickId,
              from == null || from == RegionState.UNKNOWN ? "" : " again, was " + from);
      case UNKNOWN ->
          LOG.debug(
              "{} {} has brokers never seen by the system partition up to tick {}",
              kind,
              region,
              tickId);
      default -> throw new IllegalStateException("Unexpected region state " + to);
    }
  }
}
