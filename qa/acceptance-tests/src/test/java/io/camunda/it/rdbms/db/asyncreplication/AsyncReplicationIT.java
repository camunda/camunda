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

import io.camunda.it.rdbms.db.util.MSSQLReplicationClusterContainer;
import io.camunda.it.rdbms.db.util.PostgresReplicationClusterContainer;
import io.camunda.it.rdbms.db.util.ReplicationClusterContainer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestMethodOrder(OrderAnnotation.class)
public abstract class AsyncReplicationIT<R extends ReplicationClusterContainer>
    extends AbstractAsyncReplicationIT<R> {
  private static final Logger LOG = LoggerFactory.getLogger(AsyncReplicationIT.class);

  @Test
  void shouldAcknowledgeExportedRecordsWhenReplicated() {
    final var exporterPosition = getCurrentExporterPosition();

    // when - start some process instances to generate traffic
    final int numProcessInstances = 10;
    startProcessInstances(numProcessInstances);
    waitForProcessInstancesToStart(camundaClient, numProcessInstances);

    // then - exporter advances and fully catches up
    awaitExporterPositionAdvances(exporterPosition);
    awaitExporterPositionStable(Duration.ofSeconds(2), Duration.ofSeconds(30));
    exporterAcknowledgedAll();
  }

  @Test
  @Order(1)
  void shouldNeverAcknowledgeAndStopExportingWhenReplicaIsRemoved()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given - a stable, fully acknowledged state
    final long acknowledgedPositionBeforeRemoval = getCurrentAcknowledgedExporterPosition();
    LOG.info("Position before removing the replica {}", acknowledgedPositionBeforeRemoval);

    // when - the required read replica is removed
    final var beforeStopping = Instant.now();
    LOG.info("Stopping replica at {}", beforeStopping);
    final var stopFuture = cluster.stopReplica();
    final var afterStopping = Instant.now();
    LOG.info("Disconnected replica at {}", afterStopping);
    startProcessInstances(10);

    wait(getMaxLag().plusSeconds(3));
    stopFuture.get(15, TimeUnit.MINUTES);
    LOG.info("Stopped replica at {}", Instant.now());

    // then - the exporter does not acknowledge them
    assertAcknowledgedPositionNotAdvancedBeyond(acknowledgedPositionBeforeRemoval);

    // when - more traffic is generated

    final long exportedPositionAfterMaxLag = getCurrentExporterPosition();
    startProcessInstances(10);

    // then - the exporter should not export anything
    awaitExporterPositionStable(Duration.ofSeconds(5), Duration.ofMinutes(1));

    assertThat(getCurrentExporterPosition()).isEqualTo(exportedPositionAfterMaxLag);
    assertAcknowledgedPositionNotAdvancedBeyond(acknowledgedPositionBeforeRemoval);
  }

  @Test
  @Order(2)
  void shouldResumeExportingAndAcknowledgeWhenReplicaRecovers()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given - exporter is paused after replica was removed (state left by test @Order(1))
    final long exportedPositionBeforeRecovery = getCurrentExporterPosition();
    final long acknowledgedPositionBeforeRecovery = getCurrentAcknowledgedExporterPosition();

    // when - the replica is brought back, re-establishing the replication quorum
    cluster.startReplica().get(5, TimeUnit.MILLISECONDS);

    // then - the exporter resumes and fully catches up
    awaitExporterPositionAdvances(exportedPositionBeforeRecovery);
    awaitAcknowledgedPositionAdvances(acknowledgedPositionBeforeRecovery);
    exporterAcknowledgedAll();
  }
}

class PostgresAsyncReplicationIT extends AsyncReplicationIT<PostgresReplicationClusterContainer> {

  @Override
  protected PostgresReplicationClusterContainer createCluster() {
    return new PostgresReplicationClusterContainer();
  }
}

class MssqlAsyncReplicationIT extends AsyncReplicationIT<MSSQLReplicationClusterContainer> {

  @Override
  protected MSSQLReplicationClusterContainer createCluster() {
    return new MSSQLReplicationClusterContainer();
  }
}
