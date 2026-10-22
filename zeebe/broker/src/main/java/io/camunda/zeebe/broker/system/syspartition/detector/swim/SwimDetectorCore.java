/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import io.camunda.zeebe.broker.system.configuration.SwimCfg;
import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionLayout;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionState;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.RegionFailoverDecision.CommittedFailover;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The logic of the SWIM failure detector, apart from messaging, scheduling and the system
 * partition: it tracks this broker's view of the membership, collects the views of the other
 * brokers, consolidates them, and, while this broker leads the system partition, decides on region
 * failovers.
 *
 * <p>Every system partition broker consolidates the views it receives, so the cluster's view can be
 * observed everywhere, but only the leader decides, and the decision is committed through the
 * system partition, whose quorum is the final safeguard.
 *
 * <p>A new leader waits for one report timeout before it decides, so every broker that is alive has
 * had the chance to deliver a fresh report. It needs no time to measure how long a broker has been
 * gone, since observers report that themselves.
 *
 * <p>Detecting a region as gone does not make the leader act: it starts the recovery window, and
 * only a region that stays gone, with a healthy target, for all of it is failed over from. A new
 * leader starts the window over.
 *
 * <p>Not thread-safe; used on the failure detector's actor.
 */
final class SwimDetectorCore {
  private final String localMemberId;
  private final RegionLayout layout;
  private final LongSupplier nanoClock;
  private final long reportTimeoutNanos;
  private final long recoveryWindowNanos;
  private final long epoch;
  private final SwimMembershipTracker tracker;
  private final SwimReportCollector collector;
  private final SwimViewConsolidator consolidator;
  private final SwimRegionFailoverDecider decider;
  private final Set<String> recoveredRegions = new HashSet<>();

  private long sequence;
  private boolean leader;
  private long term;
  private long leaderSinceNanos;
  private @Nullable CommittedFailover previousFailover;
  private @Nullable Pending pending;

  /**
   * @param localMemberId this broker
   * @param observers the brokers running the system partition, which report and are listened to
   * @param brokers every broker of the cluster, whose liveness is tracked
   * @param nanoClock a monotonic clock in nanoseconds
   */
  SwimDetectorCore(
      final String localMemberId,
      final Set<String> observers,
      final Set<String> brokers,
      final RegionLayout layout,
      final SwimCfg cfg,
      final LongSupplier nanoClock) {
    this(localMemberId, observers, brokers, layout, cfg, nanoClock, System.currentTimeMillis());
  }

  /**
   * @param epoch identifies this run of the broker in its reports, so reports of a restarted broker
   *     are not mistaken for old ones
   */
  SwimDetectorCore(
      final String localMemberId,
      final Set<String> observers,
      final Set<String> brokers,
      final RegionLayout layout,
      final SwimCfg cfg,
      final LongSupplier nanoClock,
      final long epoch) {
    this.localMemberId = localMemberId;
    this.layout = layout;
    this.nanoClock = nanoClock;
    reportTimeoutNanos = cfg.getReportTimeout().toNanos();
    recoveryWindowNanos = cfg.getRecoveryWindow().toNanos();
    this.epoch = epoch;
    tracker = new SwimMembershipTracker(localMemberId, brokers, nanoClock);
    collector = new SwimReportCollector(observers);
    consolidator =
        new SwimViewConsolidator(
            brokers, cfg.getDetectionWindow().toMillis(), cfg.getMinObservers());
    decider = new SwimRegionFailoverDecider(layout);
  }

  void onAlive(final String member) {
    tracker.onAlive(member);
  }

  void onSuspect(final String member) {
    tracker.onSuspect(member);
  }

  void onDead(final String member) {
    tracker.onDead(member);
  }

  void reconcile(final Map<String, Boolean> listed) {
    tracker.reconcile(listed);
  }

  /**
   * Builds this broker's report of its view and counts it as received by itself, so its own view
   * takes part in the consolidation like everyone else's.
   */
  SwimReport report() {
    final var report = new SwimReport(localMemberId, epoch, sequence++, tracker.observations());
    onReport(report);
    return report;
  }

  /**
   * Counts the report of another broker, unless it is stale or not from a system partition broker.
   */
  void onReport(final SwimReport report) {
    collector.onReport(report, nanoClock.getAsLong());
  }

  void becomeLeader(final long term, final @Nullable CommittedFailover previous) {
    leader = true;
    this.term = term;
    leaderSinceNanos = nanoClock.getAsLong();
    previousFailover = previous;
    recoveredRegions.clear();
    pending = null;
  }

  void stepDown() {
    leader = false;
    pending = null;
  }

  /** Records the latest committed failover, whoever decided it. */
  void onFailoverCommitted(final @Nullable CommittedFailover failover) {
    previousFailover = failover;
    pending = null;
    if (failover != null) {
      recoveredRegions.remove(failover.failedRegion());
    }
  }

  /**
   * Consolidates the fresh reports and, if this broker is a leader past its grace period, decides
   * whether to fail over.
   *
   * @param decidedAtMillis the wall clock time to record with a decision
   */
  Evaluation evaluate(final long decidedAtMillis) {
    final var now = nanoClock.getAsLong();
    final var fresh = collector.fresh(now, reportTimeoutNanos);
    final var views = consolidator.consolidate(fresh);
    final var regionStates =
        layout.regionStates(
            member -> views.getOrDefault(member, BrokerView.unknown(member)).state());
    markRecovered(views);

    Optional<RegionFailoverDecision> failover = Optional.empty();
    Optional<Detection> detection = Optional.empty();
    final var candidate =
        leader && now - leaderSinceNanos >= reportTimeoutNanos
            ? decider.decide(
                views, localMemberId, term, decidedAtMillis, previousFailover, recoveredRegions)
            : Optional.<RegionFailoverDecision>empty();
    if (candidate.isPresent()) {
      final var region = candidate.get();
      if (pending == null || !pending.isFor(region)) {
        pending = new Pending(region.failedRegion(), region.targetRegion(), now, decidedAtMillis);
      }
      final var waitedNanos = now - pending.detectedAtNanos;
      detection =
          Optional.of(
              new Detection(
                  region.failedRegion(),
                  region.targetRegion(),
                  pending.detectedAtMillis,
                  TimeUnit.NANOSECONDS.toMillis(waitedNanos)));
      if (waitedNanos >= recoveryWindowNanos) {
        failover = Optional.of(region.withDetectedAt(pending.detectedAtMillis));
      }
    } else {
      // the region came back, the target is not healthy anymore, or this broker does not lead
      pending = null;
    }
    return new Evaluation(views, regionStates, failover, detection, fresh.size());
  }

  /** A broker of the failed region that is seen again makes its next failure a new one. */
  private void markRecovered(final Map<String, BrokerView> views) {
    if (previousFailover == null) {
      return;
    }
    final var failed = previousFailover.failedRegion();
    final var seen =
        layout.members().getOrDefault(failed, Set.of()).stream()
            .anyMatch(
                member ->
                    views.getOrDefault(member, BrokerView.unknown(member)).state()
                        == MemberLivenessState.HEALTHY);
    if (seen) {
      recoveredRegions.add(failed);
    }
  }

  /**
   * The reports, and what they add up to, at one point in time.
   *
   * @param failover the failover to commit now: the recovery window has passed
   * @param detection a region detected as gone, which the leader will fail over from unless it
   *     recovers within the recovery window; also present when {@code failover} is
   */
  record Evaluation(
      Map<String, BrokerView> views,
      Map<String, RegionState> regionStates,
      Optional<RegionFailoverDecision> failover,
      Optional<Detection> detection,
      int freshReports) {}

  /**
   * A region the leader detected as gone and waits the recovery window for.
   *
   * @param detectedAtMillis when it was first detected, by the leader's wall clock
   * @param waitedMillis how long the leader has waited since
   */
  record Detection(
      String failedRegion, String targetRegion, long detectedAtMillis, long waitedMillis) {}

  private record Pending(
      String failedRegion, String targetRegion, long detectedAtNanos, long detectedAtMillis) {
    boolean isFor(final RegionFailoverDecision decision) {
      return failedRegion.equals(decision.failedRegion())
          && targetRegion.equals(decision.targetRegion());
    }
  }
}
