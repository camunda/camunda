/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.ClusterRestoreRequest;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Data;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Brokers restarted while every physical tenant is recovering must stay routable, so the restore
 * can still be requested (#65040). {@link TestCluster#availableGateway()} only returns ready nodes,
 * like a Kubernetes Service.
 */
@Timeout(600)
@ZeebeIntegration
final class PhysicalTenantRecoveryRestartReadinessIT {

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_A = "tenanta";
  private static final Set<String> ALL_TENANTS = Set.of(DEFAULT_TENANT, TENANT_A);

  private static final int BROKERS_COUNT = 2;
  private static final int PARTITIONS_COUNT = 2;
  private static final long BACKUP_ID = 1;
  private static final Duration TIMEOUT = Duration.ofMinutes(2);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @SuppressWarnings("resource")
  private static final ElasticsearchContainer ELASTICSEARCH =
      TestSearchContainers.createDefaultElasticsearchContainer();

  static {
    ELASTICSEARCH.start();
  }

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(
              DEFAULT_TENANT,
              Storage.elasticsearch(
                  "http://" + ELASTICSEARCH.getHttpHostAddress(), "defaultprefix"))
          .withTenant(
              TENANT_A,
              Storage.elasticsearch(
                  "http://" + ELASTICSEARCH.getHttpHostAddress(), "tenantaprefix"))
          .build();

  @TempDir private static Path defaultBackupDir;
  @TempDir private static Path tenantABackupDir;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(BROKERS_COUNT)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(BROKERS_COUNT)
          .withEmbeddedGateway(true)
          .withBrokerConfig(
              broker ->
                  configureBackupStores(
                      TENANTS.configure(broker.withUnauthenticatedAccess().withCreateSchema(true))))
          .build();

  @Test
  void shouldAcceptRestoreThroughAReadyNodeAfterEveryBrokerRestartedWhileAllTenantsRecover() {
    final var processId = "recovery-restart-process";
    final var jobType = "recovery-restart-job";

    // given - every tenant has a pending job on every partition, captured by a backup
    try (final var defaultClient = newClient(DEFAULT_TENANT);
        final var tenantAClient = newClient(TENANT_A)) {
      for (final var client : List.of(defaultClient, tenantAClient)) {
        InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
            client, processId, jobType, PARTITIONS_COUNT);
      }
      ALL_TENANTS.forEach(
          tenant -> InProcessRestoreTestUtil.takeSnapshotOnEveryBroker(cluster, tenant));
      ALL_TENANTS.forEach(
          tenant -> InProcessRestoreTestUtil.takeBackup(cluster, tenant, BACKUP_ID));

      // and - every tenant of the cluster is put into recovery
      InProcessRestoreTestUtil.changeClusterMode(defaultClient, null, "RECOVERING", false);
      ALL_TENANTS.forEach(tenant -> awaitPartitionsOf(tenant, PartitionStateCode.RECOVERING));
    }

    // when - every broker restarts while the tenants are still recovering
    cluster.shutdown().start();
    ALL_TENANTS.forEach(tenant -> awaitPartitionsOf(tenant, PartitionStateCode.RECOVERING));

    // then - every broker becomes ready again, so the restore can be routed to one of them
    Awaitility.await("every restarted broker is ready while all tenants are recovering")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(cluster.brokers().values())
                    .allSatisfy(broker -> broker.healthActuator().ready()));

    // when - the cluster-wide restore is requested through a ready node
    try (final var defaultClient = newClient(DEFAULT_TENANT);
        final var tenantAClient = newClient(TENANT_A)) {
      final var changeId = awaitClusterRestoreAccepted(defaultClient);

      // then - the restore completes and the backed-up jobs are back
      awaitRestoreCompleted(defaultClient, changeId);
      for (final var client : List.of(defaultClient, tenantAClient)) {
        InProcessRestoreTestUtil.activateAndCompleteJobsFromEveryPartition(
            client, jobType, PARTITIONS_COUNT);
      }
    }

    // and - every broker is still ready once the restore ended recovery
    assertThat(cluster.brokers().values()).allSatisfy(broker -> broker.healthActuator().ready());
  }

  private CamundaClient newClient(final String tenantId) {
    return TENANTS.newClientBuilder(cluster.availableGateway(), tenantId).build();
  }

  private void awaitPartitionsOf(final String physicalTenantId, final PartitionStateCode state) {
    Awaitility.await("partitions of " + physicalTenantId + " are " + state)
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(cluster.brokers().values())
                    .allSatisfy(
                        broker ->
                            assertThat(
                                    ClusterActuator.of(broker)
                                        .getTopology(physicalTenantId)
                                        .getBrokers())
                                .flatExtracting(BrokerState::getPartitions)
                                .isNotEmpty()
                                .extracting(PartitionState::getState)
                                .containsOnly(state)));
  }

  /** Retried: the preceding mode change may still be settling (409). */
  private static long awaitClusterRestoreAccepted(final CamundaClient client) {
    final var body = new ClusterRestoreRequest().backupIds(List.of(BACKUP_ID));
    final var changeId = new long[1];
    Awaitility.await("cluster-wide restore is accepted")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var response =
                  InProcessRestoreTestUtil.sendClusterRestoreRequest(client, null, body);
              assertThat(response.statusCode())
                  .describedAs("cluster restore REST response: %s", response.body())
                  .isEqualTo(202);
              changeId[0] = OBJECT_MAPPER.readTree(response.body()).path("changeId").asLong();
            });
    return changeId[0];
  }

  private static void awaitRestoreCompleted(final CamundaClient client, final long changeId) {
    Awaitility.await("cluster-wide restore completes for every tenant")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              for (final var tenant : ALL_TENANTS) {
                final var topology = InProcessRestoreTestUtil.sendTopologyRequest(client, tenant);
                assertThat(topology.statusCode()).isEqualTo(200);
                assertThat(
                        OBJECT_MAPPER
                            .readTree(topology.body())
                            .path("lastCompletedChangeId")
                            .asText())
                    .describedAs("last completed change for tenant '%s'", tenant)
                    .isEqualTo(String.valueOf(changeId));
              }
            });
  }

  private static TestStandaloneBroker configureBackupStores(final TestStandaloneBroker broker) {
    return broker
        .withDataConfig(PhysicalTenantRecoveryRestartReadinessIT::configureFilesystemBackup)
        .withPtConfig(TENANT_A, PhysicalTenantRecoveryRestartReadinessIT::configureTenantABackup);
  }

  private static void configureFilesystemBackup(final Data data) {
    configureFilesystemBackup(data.getPrimaryStorage().getBackup(), defaultBackupDir);
  }

  private static void configureTenantABackup(final Camunda camunda) {
    configureFilesystemBackup(camunda.getData().getPrimaryStorage().getBackup(), tenantABackupDir);
  }

  private static void configureFilesystemBackup(
      final PrimaryStorageBackup backup, final Path backupDir) {
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(backupDir.toAbsolutePath().toString());
  }
}
