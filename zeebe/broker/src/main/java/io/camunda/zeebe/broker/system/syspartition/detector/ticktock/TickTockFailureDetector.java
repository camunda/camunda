/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.TickTockCfg;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueReader;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueReader.ValueListener;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueWriter;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionListener;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionLayout;
import io.camunda.zeebe.scheduler.Actor;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Detects brokers gone from the cluster with ticks committed to the system partition's key-value
 * store. The leader puts a tick every interval; every broker, the leader included, acknowledges
 * each tick it applies with a tock to that leader, which commits the tocks with its next tick,
 * along with the cluster view they produce: the latest tick each broker was seen at. Both are plain
 * puts, so replaying the log rebuilds them on every broker.
 *
 * <p>In a cluster with regions, every broker also derives each region's state from the states of
 * its brokers, and the leader commits a region failover once when a data region failed while
 * another is active.
 *
 * <p>While this broker replays the entries it had committed when it started, the view just
 * rebuilds: it neither acknowledges ticks nor decides which brokers are gone until the next tick
 * applied after the replay.
 */
public final class TickTockFailureDetector extends Actor implements SystemPartitionListener {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final SystemPartition systemPartition;
  private final SystemKeyValueReader reader;
  private final TickPublisher publisher;
  private final TockResponder responder;
  private final BrokerLivenessEvaluator evaluator;
  private final RegionLivenessEvaluator regionEvaluator;
  private final TickTockMetrics metrics;
  private final ValueListener tickListener = (key, value) -> actor.run(this::onTick);
  private final ValueListener failoverListener = (key, value) -> actor.run(this::onRegionFailover);
  private boolean replayed;

  public TickTockFailureDetector(
      final SystemPartition systemPartition,
      final ClusterCommunicationService communicationService,
      final String localMemberId,
      final Set<String> members,
      final RegionLayout regionLayout,
      final TickTockCfg cfg,
      final MeterRegistry meterRegistry) {
    super("SystemPartitionFailureDetector", SystemPartition.PARTITION_ID);
    this.systemPartition = systemPartition;
    reader = systemPartition.keyValueReader();
    metrics = new TickTockMetrics(meterRegistry);
    final var thresholds =
        new LivenessThresholds(
            cfg.getEffectiveSuspectMissedTicksThreshold(), cfg.getMissedTicksThreshold());
    publisher =
        new TickPublisher(
            actor,
            communicationService,
            localMemberId,
            members,
            cfg.getTickInterval(),
            new RegionFailoverDecider(regionLayout, thresholds),
            metrics);
    responder = new TockResponder(communicationService, localMemberId, publisher::onTock);
    evaluator = new BrokerLivenessEvaluator(members, thresholds, metrics);
    regionEvaluator = new RegionLivenessEvaluator(regionLayout, metrics);
  }

  @Override
  public void onBecomingLeader(final long term, final SystemKeyValueWriter writer) {
    // the leader has applied every committed entry, so these are the latest committed ones
    final var lastTickId =
        reader
            .get(SystemPartitionKeyType.TICK, new TickValue())
            .map(TickValue::getTickId)
            .orElse(0L);
    final var committedView = lastSeenTicks();
    final var failoverCommitted =
        reader.get(SystemPartitionKeyType.REGION_FAILOVER, new RegionFailoverValue()).isPresent();
    actor.run(() -> publisher.start(writer, lastTickId, committedView, failoverCommitted));
  }

  @Override
  public void onBecomingFollower(final long term) {
    actor.run(publisher::stop);
  }

  @Override
  public void onBecomingInactive(final long term) {
    actor.run(publisher::stop);
  }

  @Override
  protected void onActorStarted() {
    reader.watch(SystemPartitionKeyType.TICK, tickListener);
    reader.watch(SystemPartitionKeyType.REGION_FAILOVER, failoverListener);
    // a failover committed before this broker started is only notified if it changes again
    onRegionFailover();
    actor.runOnCompletion(
        systemPartition.awaitReplayed(),
        (ok, error) -> {
          if (error != null) {
            LOG.warn("Failed to replay the system partition; not detecting failures", error);
            return;
          }
          replayed = true;
        });
  }

  @Override
  protected void onActorClosing() {
    reader.unwatch(SystemPartitionKeyType.TICK, tickListener);
    reader.unwatch(SystemPartitionKeyType.REGION_FAILOVER, failoverListener);
    publisher.stop();
    metrics.close();
  }

  private void onTick() {
    // read at handling time, as later ticks may have been applied since the notification
    final var tick = reader.get(SystemPartitionKeyType.TICK, new TickValue()).orElseThrow();
    metrics.setLatestTick(tick.getTickId());
    if (!replayed) {
      return;
    }
    responder.onTick(tick.getTickId(), tick.getLeaderId());
    regionEvaluator.evaluate(
        tick.getTickId(), evaluator.evaluate(tick.getTickId(), lastSeenTicks()));
  }

  private void onRegionFailover() {
    reader
        .get(SystemPartitionKeyType.REGION_FAILOVER, new RegionFailoverValue())
        .ifPresent(
            failover -> {
              metrics.setRegionFailover(
                  failover.getFailedRegion(), failover.getTargetRegion(), failover.getTickId());
              LOG.warn(
                  "Region failover from {} to {} committed at tick {} of the system partition,"
                      + " decided by {} on tocks {}",
                  failover.getFailedRegion(),
                  failover.getTargetRegion(),
                  failover.getTickId(),
                  failover.getLeaderId(),
                  failover.getTocks());
            });
  }

  private Map<String, Long> lastSeenTicks() {
    return reader
        .get(SystemPartitionKeyType.CLUSTER_VIEW, new ClusterViewValue())
        .<Map<String, Long>>map(ClusterViewValue::lastSeenTicks)
        .orElseGet(Map::of);
  }
}
