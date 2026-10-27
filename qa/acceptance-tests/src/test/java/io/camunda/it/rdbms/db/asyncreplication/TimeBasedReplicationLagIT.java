/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.asyncreplication;

import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.ReplicationType;
import io.camunda.it.rdbms.db.util.PostgresReplicationClusterContainer;
import io.camunda.it.rdbms.db.util.ReplicationClusterContainer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.search.RequiredSearch;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TimeBasedReplicationLagIT<R extends ReplicationClusterContainer>
    extends AbstractAsyncReplicationIT<R> {

  private static final Logger LOG = LoggerFactory.getLogger(TimeBasedReplicationLagIT.class);

  private static final Duration MAX_LAG = Duration.ofSeconds(20);
  private static final Duration REPLICA_DELAY_ABOVE_MAX_LAG = Duration.ofSeconds(30);
  private static final Duration REPLICA_DELAY_BELOW_MAX_LAG = Duration.ofSeconds(15);
  private static final int LAG_TRIGGERING_PROCESS_INSTANCES = 1;
  private static final int BLOCKED_PROCESS_INSTANCES = 5;
  private static final String PAUSED_GAUGE = "zeebe.rdbms.exporter.replication.paused";
  private static final String REPLICA_LAG_GAUGE = "zeebe.rdbms.exporter.replication.lag.ms";

  @Override
  protected Duration getMaxLag() {
    return MAX_LAG;
  }

  @Override
  protected ReplicationType getReplicationType() {
    return ReplicationType.TIME_LAG;
  }

  protected abstract void setReplicaApplyDelay(Duration delay);

  protected abstract void resetReplicaApplyDelay();

  /**
   * Keeps committing on the primary until closed. The apply delay only holds back commits, so once
   * the paused exporter stops writing, the replica would drain its backlog and report a lag below
   * maxLag (or none at all when idle) - steady commits keep the measured lag at the apply delay.
   */
  protected abstract AutoCloseable startCommitHeartbeat();

  @Test
  void shouldPauseAndResumeExportingWhenReplicaLagCrossesMaxLag() throws Exception {
    try (final var ignored = startCommitHeartbeat()) {
      // given - the replica lag is measured and below maxLag, so the exporter is running
      awaitExporterRunning();
      exporterAcknowledgedAll();
      LOG.info("Setting replica apply delay to {} to trigger lag", REPLICA_DELAY_ABOVE_MAX_LAG);
      setReplicaApplyDelay(REPLICA_DELAY_ABOVE_MAX_LAG);

      final long exportedPositionBeforeLag = getCurrentExporterPosition();
      LOG.info("Exported position before lag: {}", exportedPositionBeforeLag);

      // when - a committed export is held back on the still-connected replica
      LOG.info("Starting {} process instances to trigger lag", LAG_TRIGGERING_PROCESS_INSTANCES);
      startProcessInstances(LAG_TRIGGERING_PROCESS_INSTANCES);
      waitForProcessInstancesToStart(camundaClient, LAG_TRIGGERING_PROCESS_INSTANCES);
      awaitExporterPositionAdvances(exportedPositionBeforeLag);
      waitUntilExporterPausesOnMaxLag();

      // then - once lag is above maxLag, new broker records are not exported to the DB
      final long exportedPositionBeforeBlockedTraffic = getCurrentExporterPosition();
      LOG.info(
          "Exported position before blocked traffic: {}", exportedPositionBeforeBlockedTraffic);
      LOG.info(
          "Starting {} process instances to run against stopped exporter lag",
          BLOCKED_PROCESS_INSTANCES);
      startProcessInstances(BLOCKED_PROCESS_INSTANCES);
      awaitExporterPositionStable(Duration.ofSeconds(2), Duration.ofSeconds(4));
      assertThat(getCurrentExporterPosition()).isEqualTo(exportedPositionBeforeBlockedTraffic);

      // when - the replica is still connected, but its apply delay is reduced below maxLag again
      setReplicaApplyDelay(REPLICA_DELAY_BELOW_MAX_LAG);

      // then - exporting resumes and the blocked process instances become readable from RDBMS
      awaitExporterPositionAdvances(exportedPositionBeforeBlockedTraffic);
      waitForProcessInstancesToStart(
          camundaClient, LAG_TRIGGERING_PROCESS_INSTANCES + BLOCKED_PROCESS_INSTANCES);
      exporterAcknowledgedAll();
    } finally {
      resetReplicaApplyDelay();
    }
  }

  /**
   * Waits for a pause caused by the measured lag, not by an unmeasured (NULL) lag, which also
   * pauses the exporter but clears as soon as the replica reports any lag again.
   */
  private void waitUntilExporterPausesOnMaxLag() {
    Awaitility.await("RDBMS exporter pauses because replica lag exceeded maxLag")
        .atMost(REPLICA_DELAY_ABOVE_MAX_LAG.plusSeconds(10))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(exporterGauge(PAUSED_GAUGE).gauge().value()).isEqualTo(1.0);
              // an unmeasured lag is reported as NaN, which AssertJ orders above any number
              assertThat(exporterGauge(REPLICA_LAG_GAUGE).gauges())
                  .extracting(Gauge::value)
                  .anySatisfy(
                      lagMs -> assertThat(lagMs).isNotNaN().isGreaterThan(MAX_LAG.toMillis()));
            });
  }

  private void awaitExporterRunning() {
    Awaitility.await("RDBMS exporter is not paused")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(exporterGauge(PAUSED_GAUGE).gauge().value()).isEqualTo(0.0));
  }

  private RequiredSearch exporterGauge(final String name) {
    return meterRegistry.get(name).tag("exporterId", "rdbms").tag("partition", "1");
  }
}

class PostgresTimeBasedReplicationLagIT
    extends TimeBasedReplicationLagIT<PostgresReplicationClusterContainer> {

  @Override
  protected PostgresReplicationClusterContainer createCluster() {
    return new PostgresReplicationClusterContainer();
  }

  @Override
  protected void setReplicaApplyDelay(final Duration delay) {
    cluster.setReplicaApplyDelay(delay);
  }

  @Override
  protected void resetReplicaApplyDelay() {
    cluster.resetReplicaApplyDelay();
  }

  @Override
  protected AutoCloseable startCommitHeartbeat() {
    return cluster.startCommitHeartbeat(Duration.ofMillis(500));
  }
}
