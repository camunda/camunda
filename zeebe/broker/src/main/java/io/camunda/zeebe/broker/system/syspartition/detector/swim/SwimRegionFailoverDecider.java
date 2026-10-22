/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionLayout;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionState;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.RegionFailoverDecision.CommittedFailover;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Decides, from the consolidated view, whether to fail over from a data region to another: when
 * every system partition broker of a data region is dead and every one of another data region is
 * healthy. The target is the healthy data region with the highest priority. The consolidated view
 * already requires confirmation by several observers over the detection window, so the decision is
 * a plain function of it.
 *
 * <p>Only the leader decides, and only a quorum of the system partition can commit the decision, so
 * a minority cut off from the rest of the cluster never fails over.
 */
final class SwimRegionFailoverDecider {
  private final RegionLayout layout;

  SwimRegionFailoverDecider(final RegionLayout layout) {
    this.layout = layout;
  }

  /**
   * Returns the failover to carry out, if any. It is a candidate: the leader still waits the
   * recovery window after it first appears, and drops it if it disappears meanwhile.
   *
   * @param previous the latest committed failover, if any. A region that is still failed after its
   *     failover was committed is not failed over again, until a broker of it was seen
   * @param recoveredRegions the regions a broker of which was seen since {@code previous}
   */
  Optional<RegionFailoverDecision> decide(
      final Map<String, BrokerView> views,
      final String leaderId,
      final long term,
      final long decidedAtMillis,
      final @Nullable CommittedFailover previous,
      final Set<String> recoveredRegions) {
    if (layout.isEmpty()) {
      return Optional.empty();
    }

    final var states = layout.regionStates(member -> view(views, member).state());
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
    if (previous != null
        && previous.failedRegion().equals(failed.get())
        && !recoveredRegions.contains(failed.get())) {
      return Optional.empty();
    }

    // every broker of the region that is out of sight, also those not running the system partition
    final var goneBrokers =
        layout.brokers().get(failed.get()).stream()
            .map(member -> view(views, member))
            .filter(
                view ->
                    view.state() == MemberLivenessState.DEAD
                        || view.state() == MemberLivenessState.SUSPECT)
            .sorted(Comparator.comparing(BrokerView::memberId))
            .map(
                view -> new GoneBroker(view.memberId(), view.goneForMillis(), view.confirmations()))
            .toList();
    return Optional.of(
        new RegionFailoverDecision(
            failed.get(),
            target.get(),
            leaderId,
            term,
            decidedAtMillis,
            decidedAtMillis,
            goneBrokers));
  }

  private static BrokerView view(final Map<String, BrokerView> views, final String member) {
    return views.getOrDefault(member, BrokerView.unknown(member));
  }
}
