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
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public abstract class AsyncReplicationIT extends AbstractAsyncReplicationIT {

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
  void shouldNeverAcknowledgeAndStopExportingWhenReplicaIsRemoved() {
    // given - a stable, fully acknowledged state
    final long acknowledgedPositionBeforeRemoval = getCurrentAcknowledgedExporterPosition();

    // when - the required read replica is removed
    cluster.stopReplica();
    startProcessInstances(10);

    // then - the exporter still exports records but never acknowledges them
    // TODO optimize this to not wait X seconds but on a RdbmsExporter metric when implemented
    wait(MAX_LAG.plus(3, ChronoUnit.SECONDS)); // wait for the max-lag interval + X
    awaitExporterPositionAdvances(acknowledgedPositionBeforeRemoval);
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
  void shouldResumeExportingAndAcknowledgeWhenReplicaRecovers() {
    // given - exporter is paused after replica was removed (state left by test @Order(1))
    final long exportedPositionBeforeRecovery = getCurrentExporterPosition();
    final long acknowledgedPositionBeforeRecovery = getCurrentAcknowledgedExporterPosition();

    // when - the replica is brought back, re-establishing the replication quorum
    cluster.startReplica();

    // then - the exporter resumes and fully catches up
    awaitExporterPositionAdvances(exportedPositionBeforeRecovery);
    awaitAcknowledgedPositionAdvances(acknowledgedPositionBeforeRecovery);
    exporterAcknowledgedAll();
  }
}

class PostgresAsyncReplicationIT extends AsyncReplicationIT {

  @Override
  protected ReplicationClusterContainer createCluster() {
    return new PostgresReplicationClusterContainer();
  }
}

class MssqlAsyncReplicationIT extends AsyncReplicationIT {

  @Override
  protected ReplicationClusterContainer createCluster() {
    return new MSSQLReplicationClusterContainer();
  }
}

