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
import io.camunda.exporter.rdbms.replication.DefaultReplicationController;
import io.camunda.it.rdbms.db.util.PostgresReplicationClusterContainer;
import io.camunda.it.rdbms.db.util.ReplicationClusterContainer;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import java.time.Duration;
import org.apache.logging.log4j.Level;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

  private LogCapturer pauseLog;

  @BeforeAll
  void before() {
    pauseLog = LogCapturer.capturing(DefaultReplicationController.class, Level.WARN);
  }

  @AfterAll
  void after() {
    pauseLog.close();
  }

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

  @Test
  void shouldPauseAndResumeExportingWhenReplicaLagCrossesMaxLag() {
    // given - the time-based replication monitor allows up to 30s lag
    exporterAcknowledgedAll();
    LOG.info("Setting replica apply delay to {} to trigger lag", REPLICA_DELAY_ABOVE_MAX_LAG);
    setReplicaApplyDelay(REPLICA_DELAY_ABOVE_MAX_LAG);

    try {
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

  private void waitUntilExporterPausesOnMaxLag() {
    Awaitility.await("RDBMS exporter pauses because replica lag exceeded maxLag")
        .atMost(REPLICA_DELAY_ABOVE_MAX_LAG.plusSeconds(10))
        .untilAsserted(
            () -> assertThat(pauseLog.contains("[RDBMS Exporter P1] Pausing exporter")).isTrue());
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
}
