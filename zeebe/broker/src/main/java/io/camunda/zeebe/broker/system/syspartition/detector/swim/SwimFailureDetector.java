/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.SwimCfg;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueReader;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueReader.ValueListener;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueWriter;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionListener;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionLayout;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.SwimDetectorCore.Evaluation;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Detects regions gone from the cluster from the SWIM membership each broker already gossips. Every
 * system partition broker tracks its own SWIM view, and reports it to the other system partition
 * brokers every interval. Those reports are its heartbeats, and the only thing it sends: SWIM stays
 * the sole judge of reachability, and this detector only decides what the views add up to.
 *
 * <p>Each broker consolidates the reports it receives, see {@link SwimViewConsolidator}, so the
 * state of the cluster can be observed everywhere. Only the leader of the system partition decides
 * on a region failover, in two steps: once every system partition broker of a data region is
 * confirmed gone by enough observers for the detection window, the region is detected as gone; if
 * it stays gone for the recovery window after that, the leader commits the failover with the
 * evidence: which brokers were out of sight, for how long, and the region to fail over to. That
 * commit needs a quorum of the system partition, so a broker cut off from the rest can never fail a
 * region over.
 *
 * <p>Reports are plain messages, not committed: they are superseded every interval, and a lost one
 * only delays a decision.
 */
public final class SwimFailureDetector extends Actor implements SystemPartitionListener {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final SystemKeyValueReader reader;
  private final ClusterMembershipService membershipService;
  private final ClusterCommunicationService communicationService;
  private final String localMemberId;
  private final Set<MemberId> peers;
  private final SwimCfg cfg;
  private final RegionLayout layout;
  private final SwimDetectorCore core;
  private final SwimDetectorMetrics metrics;
  private final InstantSource wallClock = InstantSource.system();
  private final ClusterMembershipEventListener membershipListener = this::onMembershipEvent;
  private final ValueListener failoverListener = (key, value) -> actor.run(this::onFailoverCommit);

  private @Nullable ScheduledTimer timer;
  private @Nullable String detectedRegion;
  private @Nullable SystemKeyValueWriter writer;
  private boolean putInFlight;
  // identifies the current leadership, so completions of earlier ones are ignored
  private long leadership;

  /**
   * @param observers the brokers running the system partition
   * @param brokers every broker of the cluster, whose liveness is tracked
   */
  public SwimFailureDetector(
      final SystemPartition systemPartition,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService,
      final String localMemberId,
      final Set<String> observers,
      final Set<String> brokers,
      final RegionLayout layout,
      final SwimCfg cfg,
      final MeterRegistry meterRegistry) {
    super("SystemPartitionSwimFailureDetector", SystemPartition.PARTITION_ID);
    reader = systemPartition.keyValueReader();
    this.membershipService = membershipService;
    this.communicationService = communicationService;
    this.localMemberId = localMemberId;
    this.cfg = cfg;
    this.layout = layout;
    peers =
        observers.stream()
            .filter(observer -> !observer.equals(localMemberId))
            .map(MemberId::from)
            .collect(Collectors.toUnmodifiableSet());
    core = new SwimDetectorCore(localMemberId, observers, brokers, layout, cfg, System::nanoTime);
    metrics = new SwimDetectorMetrics(meterRegistry);
  }

  @Override
  public void onBecomingLeader(final long term, final SystemKeyValueWriter writer) {
    // the leader has applied every committed entry, so this is the latest committed failover
    final var previous =
        reader
            .get(SystemPartitionKeyType.REGION_FAILOVER, new RegionFailoverValue())
            .map(RegionFailoverValue::committed)
            .orElse(null);
    actor.run(
        () -> {
          leadership++;
          this.writer = writer;
          putInFlight = false;
          core.becomeLeader(term, previous);
        });
  }

  @Override
  public void onBecomingFollower(final long term) {
    actor.run(this::stepDown);
  }

  @Override
  public void onBecomingInactive(final long term) {
    actor.run(this::stepDown);
  }

  @Override
  protected void onActorStarting() {
    reconcile();
    membershipService.addListener(membershipListener);
    communicationService.consume(
        SwimReport.SUBJECT,
        SwimReport::decode,
        (sender, report) -> core.onReport(report),
        command -> actor.run(command));
    reader.watch(SystemPartitionKeyType.REGION_FAILOVER, failoverListener);
  }

  @Override
  protected void onActorStarted() {
    // a failover committed before this broker started is only notified if it changes again
    onFailoverCommit();
    timer = actor.runAtFixedRate(cfg.getReportInterval(), this::tick);
  }

  @Override
  protected void onActorClosing() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
    reader.unwatch(SystemPartitionKeyType.REGION_FAILOVER, failoverListener);
    communicationService.unsubscribe(SwimReport.SUBJECT);
    membershipService.removeListener(membershipListener);
    metrics.close();
  }

  private void stepDown() {
    detectedRegion = null;
    leadership++;
    writer = null;
    putInFlight = false;
    core.stepDown();
  }

  private void onMembershipEvent(final ClusterMembershipEvent event) {
    final var member = event.subject();
    final var id = member.id().id();
    final var reachable = member.isReachable();
    actor.run(
        () -> {
          switch (event.type()) {
            case MEMBER_ADDED, REACHABILITY_CHANGED -> {
              if (reachable) {
                core.onAlive(id);
              } else {
                core.onSuspect(id);
              }
            }
            case MEMBER_REMOVED -> core.onDead(id);
            default -> {
              // metadata changes say nothing about reachability
            }
          }
        });
  }

  /** Aligns the tracked view with the members SWIM lists, in case an event was missed. */
  private void reconcile() {
    final var listed = new HashMap<String, Boolean>();
    membershipService
        .getMembers()
        .forEach(member -> listed.put(member.id().id(), member.isReachable()));
    core.reconcile(listed);
  }

  private void tick() {
    reconcile();
    final var report = core.report();
    peers.forEach(
        peer ->
            communicationService.unicast(
                SwimReport.SUBJECT, report, SwimReport::encode, peer, true));

    final var evaluation = core.evaluate(wallClock.millis());
    metrics.update(evaluation);
    logDetection(evaluation);
    evaluation.failover().ifPresent(this::putFailover);
  }

  private void logDetection(final Evaluation evaluation) {
    final var detection = evaluation.detection();
    if (detection.isPresent() && !detection.get().failedRegion().equals(detectedRegion)) {
      detectedRegion = detection.get().failedRegion();
      LOG.warn(
          "Region {} was detected as gone; failing over to region {} unless it recovers within {}",
          detection.get().failedRegion(),
          detection.get().targetRegion(),
          cfg.getRecoveryWindow());
    } else if (detection.isEmpty() && detectedRegion != null) {
      LOG.info("Region {} is no longer detected as gone; not failing over from it", detectedRegion);
      detectedRegion = null;
    }
  }

  private void putFailover(final RegionFailoverDecision decision) {
    if (writer == null || putInFlight) {
      return;
    }
    putInFlight = true;
    final var currentLeadership = leadership;
    LOG.info(
        "Region {} is gone: {}. Putting a failover to region {} to the system partition",
        decision.failedRegion(),
        decision.goneBrokers(),
        decision.targetRegion());
    actor.runOnCompletion(
        writer.put(SystemPartitionKeyType.REGION_FAILOVER, RegionFailoverValue.of(decision)),
        (position, error) -> {
          if (currentLeadership != leadership) {
            return;
          }
          putInFlight = false;
          if (error == null) {
            // learn of it now, rather than when the watch fires, so the next report cannot decide
            // it
            // again
            core.onFailoverCommitted(decision.committed());
            detectedRegion = null;
          } else {
            LOG.debug(
                "Failed to put the region failover; deciding again on the next report", error);
          }
        });
  }

  /** Learns of the latest committed failover, whoever decided it. */
  private void onFailoverCommit() {
    reader
        .get(SystemPartitionKeyType.REGION_FAILOVER, new RegionFailoverValue())
        .ifPresent(
            failover -> {
              detectedRegion = null;
              core.onFailoverCommitted(failover.committed());
              metrics.setFailover(
                  failover.getFailedRegion(),
                  failover.getTargetRegion(),
                  failover.getDecidedAtMillis());
              LOG.warn(
                  "Region failover from {} to {} committed to the system partition, decided by {}"
                      + " in term {}; brokers out of sight: {}",
                  failover.getFailedRegion(),
                  failover.getTargetRegion(),
                  failover.getLeaderId(),
                  failover.getTerm(),
                  failover.getGoneBrokers());
            });
  }
}
