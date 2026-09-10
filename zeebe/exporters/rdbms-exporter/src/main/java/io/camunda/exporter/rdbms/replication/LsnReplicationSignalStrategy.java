/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.read.replication.ReplicationLsnProvider;
import io.camunda.db.rdbms.read.replication.ReplicationLsnStatus;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Uses the log sequence number (LSN) of the database to compare the replication status of the
 * primary and all linked replicas with each other. A position is only acknowledged once the LSN of
 * the required quorum of replicas has reached the LSN captured at flush time, which guarantees that
 * no data will be lost in case of a failover.
 */
public final class LsnReplicationSignalStrategy
    implements ReplicationSignalStrategy<ReplicationLsnStatus> {

  private final ReplicationLsnProvider lsnProvider;
  private final ReplicationConfiguration config;
  private final ReplicaRegionResolver regionResolver;

  public LsnReplicationSignalStrategy(
      final ReplicationLsnProvider lsnProvider, final ReplicationConfiguration config) {
    this.lsnProvider = lsnProvider;
    this.config = config;
    regionResolver = new ReplicaRegionResolver(config.getRegions());
  }

  @Override
  public long captureFlushMarker() {
    return lsnProvider.getCurrent();
  }

  @Override
  public List<ReplicationLsnStatus> fetchStatuses() {
    return lsnProvider.getReplicationStatuses();
  }

  /**
   * Calculates the lowest LSN confirmed by at least each declared region's own quorum (see {@link
   * RegionAwareQuorum}). Returns {@link #UNCONFIRMED} if the provider itself reports an unhealthy
   * position, or if quorum isn't met.
   */
  @Override
  public long computeConfirmedMarker(
      final List<ReplicationLsnStatus> statuses, final Optional<String> currentPrimaryRegion) {
    return RegionAwareQuorum.evaluate(
            statuses,
            config,
            regionResolver,
            currentPrimaryRegion,
            ReplicationLsnStatus::logStatus,
            true)
        .orElse(UNCONFIRMED);
  }

  /**
   * Falls back to {@code queueHeadAge} - how long the oldest still-unconfirmed position has been
   * waiting, or {@link Duration#ZERO} if nothing is queued - since this mode has no per-replica lag
   * figure of its own. Quorum loss is only pause-worthy while the queue is empty; a pending
   * position's own queue-head-age already signals staleness.
   */
  @Override
  public Duration computePauseLag(
      final List<ReplicationLsnStatus> statuses,
      final Optional<Duration> queueHeadAge,
      final Optional<String> currentPrimaryRegion) {
    final boolean quorumNotMet =
        queueHeadAge.isEmpty()
            && !RegionAwareQuorum.quorumMet(statuses, config, regionResolver, currentPrimaryRegion);
    if (quorumNotMet) {
      return PAUSE_WORST_CASE;
    }
    return queueHeadAge.orElse(Duration.ZERO);
  }

  @Override
  public List<String> regionsBelowQuorum(
      final List<ReplicationLsnStatus> statuses, final Optional<String> currentPrimaryRegion) {
    return RegionAwareQuorum.regionsBelowQuorum(
        statuses, config, regionResolver, currentPrimaryRegion);
  }

  /**
   * Resolved fresh on every call from the primary's live connection (never from static config), so
   * it reflects whichever region currently hosts the primary, including after a failover.
   */
  @Override
  public Optional<String> resolveCurrentPrimaryRegion() {
    return regionResolver.resolve(lsnProvider.getCurrentReplicaLabel());
  }
}
