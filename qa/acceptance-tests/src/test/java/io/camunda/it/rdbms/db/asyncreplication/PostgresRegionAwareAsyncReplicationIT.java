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

import io.camunda.configuration.RdbmsAsyncReplication;
import io.camunda.configuration.RdbmsAsyncReplication.Region;
import io.camunda.it.rdbms.db.util.PostgresReplicationClusterContainer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Exercises the region-aware replication quorum (see {@code
 * zeebe/exporters/rdbms-exporter/docs/adr/0001-region-aware-replication-quorum.md}) end-to-end
 * against a real, 2-region Postgres topology: primary (region A, credited live via its {@code
 * cluster_name}) + one secondary in region A, and two secondaries in region B. Unlike {@link
 * AsyncReplicationIT}'s single-secondary, flat {@code minSyncReplicas} setup - left as-is - this
 * proves the specific value region-awareness adds over a flat count: tolerating a partial node loss
 * within a region, while still pausing when a whole region goes dark even though the other region
 * stays fully healthy.
 */
@TestMethodOrder(OrderAnnotation.class)
class PostgresRegionAwareAsyncReplicationIT
    extends AbstractAsyncReplicationIT<PostgresReplicationClusterContainer> {

  private static final String REGION_A_PRIMARY_LABEL = "region-a-primary";
  private static final String REGION_A_NODE_1 = "region-a-1";
  private static final String REGION_B_NODE_1 = "region-b-1";
  private static final String REGION_B_NODE_2 = "region-b-2";

  @Override
  protected PostgresReplicationClusterContainer createCluster() {
    return new PostgresReplicationClusterContainer(
        List.of(REGION_A_NODE_1, REGION_B_NODE_1, REGION_B_NODE_2), REGION_A_PRIMARY_LABEL);
  }

  @Override
  protected void configureAsyncReplication(final RdbmsAsyncReplication asyncReplication) {
    asyncReplication.setRegions(
        List.of(
            // primary's live credit + the one real secondary
            region("region-a", "region-a-.*", 2),
            // tolerates losing one of its two secondaries
            region("region-b", "region-b-.*", 1)));
  }

  private static Region region(final String name, final String pattern, final int minReplicas) {
    final var region = new Region();
    region.setName(name);
    region.setPattern(pattern);
    region.setMinReplicas(minReplicas);
    return region;
  }

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
  void shouldTolerateLosingOneOfTwoNodesInRegionB()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given - a stable, fully acknowledged state
    final long acknowledgedPositionBefore = getCurrentAcknowledgedExporterPosition();

    // when - one of region B's two secondaries goes down; region B's own quorum (minReplicas=1)
    // is still met by its remaining node, and region A is untouched
    cluster.stopNode(REGION_B_NODE_1).get(15, TimeUnit.MINUTES);
    startProcessInstances(10);

    // then - the exporter is unaffected and keeps acknowledging normally
    awaitAcknowledgedPositionAdvances(acknowledgedPositionBefore);
    exporterAcknowledgedAll();
  }

  @Test
  @Order(2)
  void shouldPauseWhenRegionAsOnlySecondaryIsLost()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given - a stable state, with region B's node-1 still down from the previous test
    final long acknowledgedPositionBefore = getCurrentAcknowledgedExporterPosition();

    // when - region A's only secondary goes down too; region A now has just the primary's own
    // live credit (count 1) against its minReplicas=2, so it falls short - even though region B's
    // remaining node (node-2) is fully healthy, and a flat "any replica confirmed" count would
    // have been satisfied by it alone
    cluster.stopNode(REGION_A_NODE_1).get(15, TimeUnit.MINUTES);
    startProcessInstances(10);

    wait(getMaxLag().plusSeconds(3));

    // then - the exporter does not acknowledge past the pre-outage baseline
    assertAcknowledgedPositionNotAdvancedBeyond(acknowledgedPositionBefore);

    // when - more traffic is generated
    final long exportedPositionAfterMaxLag = getCurrentExporterPosition();
    startProcessInstances(10);

    // then - the exporter should not export anything further either
    awaitExporterPositionStable(Duration.ofSeconds(5), Duration.ofMinutes(1));
    assertThat(getCurrentExporterPosition()).isEqualTo(exportedPositionAfterMaxLag);
    assertAcknowledgedPositionNotAdvancedBeyond(acknowledgedPositionBefore);
  }

  @Test
  @Order(3)
  void shouldResumeWhenBothRegionsRecover()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given - exporter paused after region A went dark (state left by @Order(2))
    final long exportedPositionBeforeRecovery = getCurrentExporterPosition();
    final long acknowledgedPositionBeforeRecovery = getCurrentAcknowledgedExporterPosition();

    // when - both regions' downed nodes come back, re-establishing every region's quorum
    cluster.startNode(REGION_A_NODE_1).get(5, TimeUnit.MINUTES);
    cluster.startNode(REGION_B_NODE_1).get(5, TimeUnit.MINUTES);

    // then - the exporter resumes and fully catches up
    awaitExporterPositionAdvances(exportedPositionBeforeRecovery);
    awaitAcknowledgedPositionAdvances(acknowledgedPositionBeforeRecovery);
    exporterAcknowledgedAll();
  }
}
