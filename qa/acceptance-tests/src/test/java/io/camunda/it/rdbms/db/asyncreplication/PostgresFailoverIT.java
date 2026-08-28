/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.asyncreplication;

import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForUser;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.ReplicationType;
import io.camunda.exporter.rdbms.RdbmsExporter;
import io.camunda.it.rdbms.db.util.PostgresReplicationClusterContainer;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("async-repl")
class PostgresFailoverIT extends AbstractAsyncReplicationIT<PostgresReplicationClusterContainer> {

  @Override
  protected PostgresReplicationClusterContainer createCluster() {
    return new PostgresReplicationClusterContainer();
  }

  @Override
  protected Duration getFlushInterval() {
    return Duration.ZERO;
  }

  @Override
  protected String jdbcUrl(final PostgresReplicationClusterContainer cluster) {
    // Lists both nodes so the JDBC driver can transparently keep connecting to whichever one is
    // currently writable once the replica is promoted - see getFailoverJdbcUrl() for details.
    return cluster.getFailoverJdbcUrl();
  }

  @Override
  protected ReplicationType getReplicationType() {
    return ReplicationType.DELAY;
  }

  @Test
  void shouldReplayMissingRecordsAfterPostgresFailover() throws Exception {
    // given - some baseline traffic (a process instance and a user) fully replicated and
    // acknowledged before the outage starts
    startProcessInstances(5);
    final var baselineUser = "failover-baseline-user-" + UUID.randomUUID();
    createUser(baselineUser);
    waitForProcessInstancesToStart(camundaClient, 5);
    waitForUser(camundaClient, baselineUser);
    exporterAcknowledgedAll();
    final long acknowledgedPositionBeforeOutage = getCurrentAcknowledgedExporterPosition();

    // when - the read replica disconnects from the primary
    cluster.disconnectReplicaFromPrimary();

    // and - more traffic is written to the primary only, and is never replicated
    final int duringOutageProcessInstances = 10;
    startProcessInstances(duringOutageProcessInstances);
    final var duringOutageUser = "failover-outage-user-" + UUID.randomUUID();
    createUser(duringOutageUser);

    // sanity check - keep this within the DELAY window so outage traffic is not acknowledged yet
    Awaitility.await("exporter position advances while acknowledgments are still delayed")
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(getCurrentExporterPosition())
                    .isGreaterThan(acknowledgedPositionBeforeOutage));
    assertAcknowledgedPositionNotAdvancedBeyond(acknowledgedPositionBeforeOutage);

    try (final var replayLog = LogCapturer.capturing(RdbmsExporter.class, Level.INFO)) {
      // and - the primary is shut down and the disconnected replica is promoted to take its place
      cluster.stopPrimary().get(1, TimeUnit.MINUTES);
      cluster.promoteReplica();

      // and - one more process instance is started against the newly-promoted primary. The
      // exporter only discovers that the database fell behind the broker's acknowledged position
      // when it next attempts a flush (the EXPORTER_POSITION row-lock hook is what raises the
      // mismatch)
      startProcessInstances(1);

      // then - the exporter actually requested a replay from the broker
      Awaitility.await("RdbmsExporter requests a replay to recover the missing records")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(replayLog.contains("Requesting replay from")).isTrue());
    }

    // and - the exporter detects that the now-connected (promoted) database is behind the
    // broker's acknowledged position, replays the records missed during the outage, and they -
    // together with the pre-outage baseline - become visible again through the client
    waitForUser(camundaClient, baselineUser);
    waitForUser(camundaClient, duringOutageUser);
    waitForProcessInstancesToStart(camundaClient, 5 + duringOutageProcessInstances + 1);
    awaitAcknowledgedPositionAdvances(acknowledgedPositionBeforeOutage);
    exporterAcknowledgedAll();
  }

  private void createUser(final String username) {
    camundaClient
        .newCreateUserCommand()
        .username(username)
        .name(username)
        .password("password")
        .email(username + "@example.com")
        .send()
        .join();
  }
}
