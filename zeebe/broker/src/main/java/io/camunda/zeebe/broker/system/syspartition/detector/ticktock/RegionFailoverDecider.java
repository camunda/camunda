/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import io.camunda.zeebe.broker.system.syspartition.detector.RegionLayout;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionState;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Decides, from a tick and the cluster view it commits, whether to fail over from a data region to
 * another: when every broker of a data region is dead and every broker of another data region is
 * healthy. The target is the healthy data region with the highest priority. The decision is a pure
 * function of committed values, so the command carries everything needed to reproduce it.
 *
 * <p>Only the leader decides, and only a quorum of the system partition can commit its tick, so a
 * minority cut off from the rest of the cluster never fails over.
 */
final class RegionFailoverDecider {
  private final RegionLayout layout;
  private final LivenessThresholds thresholds;

  RegionFailoverDecider(final RegionLayout layout, final LivenessThresholds thresholds) {
    this.layout = layout;
    this.thresholds = thresholds;
  }

  /**
   * Returns the failover to commit with the given tick, if any.
   *
   * @param previous the latest committed failover, if any. A region that is still failed after its
   *     failover was committed is not failed over again; once a broker of it was seen after that
   *     failover, it recovered, so its next failure is a new one.
   */
  Optional<RegionFailoverValue> decide(
      final long tickId,
      final Map<String, Long> lastSeenTicks,
      final String leaderId,
      final @Nullable CommittedFailover previous) {
    if (layout.isEmpty()) {
      return Optional.empty();
    }

    final var states =
        layout.regionStates(member -> thresholds.stateOf(tickId, lastSeenTicks.get(member)));
    final var failed =
        layout.dataRegions().stream()
            .filter(region -> states.get(region) == RegionState.FAILED)
            .findFirst();
    final var target =
        layout.dataRegions().stream()
            .filter(region -> states.get(region) == RegionState.ACTIVE)
            .findFirst();
    if (failed.isEmpty() || target.isEmpty()) {
      return Optional.empty();
    }
    if (previous != null && previous.failedRegion().equals(failed.get())) {
      final var seenSince =
          layout.members().get(failed.get()).stream()
              .anyMatch(member -> lastSeenTicks.getOrDefault(member, 0L) > previous.tickId());
      if (!seenSince) {
        return Optional.empty();
      }
    }

    return Optional.of(
        new RegionFailoverValue()
            .setFailedRegion(failed.get())
            .setTargetRegion(target.get())
            .setTickId(tickId)
            .setLeaderId(leaderId)
            .setTocks(lastSeenTicks));
  }

  /** The part of a committed failover that tells whether a later failure is a new one. */
  record CommittedFailover(String failedRegion, long tickId) {
    static CommittedFailover of(final RegionFailoverValue failover) {
      return new CommittedFailover(failover.getFailedRegion(), failover.getTickId());
    }
  }
}
