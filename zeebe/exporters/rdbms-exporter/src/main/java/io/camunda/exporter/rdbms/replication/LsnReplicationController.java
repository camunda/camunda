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
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.List;

/**
 * The LsnReplicationController uses the log sequence number (LSN) of the database to compare the
 * replication status of the primary and all linked replicas with each other. We only
 * acknowledge/commit exporter positions to the ExporterController if the LSN of the replicas has
 * reached the LSN of the primary at the time of the flush. <br>
 */
public class LsnReplicationController
    extends AbstractReplicationController<LsnReplicationController.LsnPositionEntry> {

  private final ReplicationLsnProvider lsnProvider;

  public LsnReplicationController(
      final Controller controller,
      final ReplicationLsnProvider lsnProvider,
      final ReplicationConfiguration replicationConfiguration,
      final int partitionId,
      final InstantSource clock,
      final RdbmsWriterMetrics metrics) {
    super(controller, replicationConfiguration, partitionId, clock, metrics);
    this.lsnProvider = lsnProvider;
  }

  /**
   * Tracks the current LSN and link it to the current exporter position. This pair is remembered in
   * a list until it is confirmed by the connected replicas.
   *
   * @param exporterPosition current exported position
   */
  @Override
  public void onFlush(final long exporterPosition) {
    onFlushCapturing(
        exporterPosition,
        lsnProvider::getCurrent,
        currentLsn -> new LsnPositionEntry(exporterPosition, currentLsn, clock.millis()));
  }

  /**
   * Retrieve the replication status from the connected replicas and confirm all older pairs of
   * LSN+position to the exporter controller.<br>
   * <br>
   * If the oldest still not replicated position is older than the configured <code>maxLag</code>,
   * the replication is marked as <i>out-of-sync</i>.
   */
  @Override
  protected void doCheckReplication() {
    final var statuses = lsnProvider.getReplicationStatuses();
    final int connectedReplicas = statuses.size();

    final long confirmedLsn = computeConfirmedLsn(statuses);
    final var confirmedEntry = drainConfirmed(entry -> entry.lsn() <= confirmedLsn);
    final Duration dbReplicationLag = getCurrentDbLag();

    log.debug(
        "[RDBMS Exporter P{}] Confirmed LSN {}, current lag is {}",
        partitionId,
        confirmedLsn,
        dbReplicationLag);

    final boolean dbLagExceeded = isMaxLagExceeded(dbReplicationLag);
    final boolean quorumNotMet = isQueueEmpty() && connectedReplicas < config.getMinSyncReplicas();

    // pause when:
    // - pauseOnMaxLagExceeded is true
    // - either the replicas have not confirmed for long time
    // - or there are no pending entries and there are not enough replicas connected
    updatePausedState(
        config.isPauseOnMaxLagExceeded() && (dbLagExceeded || quorumNotMet),
        dbReplicationLag,
        connectedReplicas);

    if (confirmedEntry != null) {
      acknowledge(confirmedEntry, dbReplicationLag, connectedReplicas);
    }

    recordMetrics(statuses);
  }

  @VisibleForTesting
  Duration getCurrentDbLag() {
    final var head = peekPending();
    if (head == null) {
      return Duration.ZERO;
    }
    return Duration.ofMillis(clock.millis() - head.enqueueTimeMs());
  }

  @VisibleForTesting
  boolean isMaxLagExceeded(final Duration dbLag) {
    return dbLag.compareTo(config.getMaxLag()) > 0;
  }

  /**
   * Calculates the lowest LSN which is confirmed by at least the configured <code>minSyncReplicas
   * </code> connected replicas.<br>
   * The number of entries in <code>statuses</code> may vary over time since replicas may disconnect
   * on network failure or when new, optional replicas are connected. The <code>minSyncReplicas
   * </code> configuration property defines a minimal quorum of replicas in sync.
   *
   * @param statuses the replication status read from all connected replicas.
   * @return the lowest LSN confirmed.
   */
  @VisibleForTesting
  long computeConfirmedLsn(final List<ReplicationLsnStatus> statuses) {
    if (lsnProvider.getCurrent() < 0) {
      return Long.MIN_VALUE;
    }

    if (statuses.size() < config.getMinSyncReplicas()) {
      return -1;
    }

    return statuses.stream()
        .map(ReplicationLsnStatus::logStatus)
        .sorted(Comparator.<Long>naturalOrder().reversed())
        .limit(config.getMinSyncReplicas())
        .min(Comparator.naturalOrder())
        .orElse(-1L);
  }

  /**
   * An entry linking an LSN to an exporter position.
   *
   * @param position the exporter position
   * @param lsn the LSN
   * @param enqueueTimeMs the instant in ms when the LSN was queried
   */
  record LsnPositionEntry(long position, long lsn, long enqueueTimeMs) implements PendingEntry {}
}
