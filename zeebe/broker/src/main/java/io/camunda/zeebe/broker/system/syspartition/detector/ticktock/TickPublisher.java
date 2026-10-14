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
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueWriter;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Puts a tick every interval while this broker leads the system partition, carrying the tocks
 * received since its previous tick. Before each tick it puts the cluster view those tocks and the
 * tick itself produce, so both are committed facts and replaying the log rebuilds them. At most one
 * tick is uncommitted at a time, so ticks do not pile up in the log while the partition cannot
 * commit. Runs on the failure detector's actor.
 */
final class TickPublisher {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final ActorControl actor;
  private final ClusterCommunicationService communicationService;
  private final String localMemberId;
  private final Set<String> members;
  private final Duration tickInterval;
  private final TickTockMetrics metrics;
  // the latest tick each broker, this one included, acknowledged since the previous tick
  private final Map<String, Long> tocks = new HashMap<>();
  // the latest tick each broker was seen at, including the ticks put but not committed yet
  private final Map<String, Long> lastSeenTicks = new TreeMap<>();

  private @Nullable SystemKeyValueWriter writer;
  private @Nullable ScheduledTimer timer;
  private long nextTickId;
  private boolean tickPending;
  // identifies the current leadership, so completions of earlier ones are ignored
  private long leadership;

  TickPublisher(
      final ActorControl actor,
      final ClusterCommunicationService communicationService,
      final String localMemberId,
      final Set<String> members,
      final Duration tickInterval,
      final TickTockMetrics metrics) {
    this.metrics = metrics;
    this.actor = actor;
    this.communicationService = communicationService;
    this.localMemberId = localMemberId;
    this.members = members;
    this.tickInterval = tickInterval;
  }

  /**
   * Starts ticking for a new leadership.
   *
   * @param writer the key-value writer of the leadership
   * @param lastTickId the id of the latest committed tick, or 0 if none
   * @param committedView the latest committed cluster view
   */
  void start(
      final SystemKeyValueWriter writer,
      final long lastTickId,
      final Map<String, Long> committedView) {
    stop();
    this.writer = writer;
    nextTickId = lastTickId + 1;
    lastSeenTicks.putAll(committedView);
    communicationService.consume(Tock.SUBJECT, Tock::decode, this::onTock, actor::run);
    // tick right away, so ticks continue without a further gap after the election
    actor.submit(this::tick);
    timer = actor.runAtFixedRate(tickInterval, this::tick);
  }

  /** Stops ticking, if it was; the tocks received so far are dropped. */
  void stop() {
    if (writer == null) {
      return;
    }
    leadership++;
    writer = null;
    tickPending = false;
    tocks.clear();
    lastSeenTicks.clear();
    communicationService.unsubscribe(Tock.SUBJECT);
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

  /** Buffers the tock for the next tick, if this broker is ticking. */
  void onTock(final Tock tock) {
    // a tock of a tick not put yet cannot be genuine
    if (writer == null || !members.contains(tock.memberId()) || tock.tickId() >= nextTickId) {
      return;
    }
    metrics.tockReceived(tock.memberId());
    tocks.merge(tock.memberId(), tock.tickId(), Math::max);
  }

  private void tick() {
    if (writer == null || tickPending) {
      return;
    }
    tickPending = true;
    final var tickId = nextTickId++;
    final var currentLeadership = leadership;
    tocks.forEach((member, tockTickId) -> lastSeenTicks.merge(member, tockTickId, Math::max));
    // the view first, so watchers of the tick read the view it produced
    writer.put(
        SystemPartitionKeyType.CLUSTER_VIEW,
        new ClusterViewValue().setLastSeenTicks(lastSeenTicks));
    final var tick = new TickValue().setTickId(tickId).setLeaderId(localMemberId).setTocks(tocks);
    tocks.clear();
    final var putAt = System.nanoTime();
    actor.runOnCompletion(
        writer.put(SystemPartitionKeyType.TICK, tick),
        (position, error) -> {
          if (currentLeadership != leadership) {
            return;
          }
          tickPending = false;
          if (error == null) {
            metrics.recordTickCommitLatency(Duration.ofNanos(System.nanoTime() - putAt));
          } else {
            LOG.debug("Failed to put tick {}; the next tick follows it", tickId, error);
          }
        });
  }
}
