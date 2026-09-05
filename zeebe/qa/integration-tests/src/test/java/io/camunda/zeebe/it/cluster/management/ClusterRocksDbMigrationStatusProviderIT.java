/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.admin.ClusterRocksDbMigrationStatusProvider;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Real multi-broker coverage for {@link ClusterRocksDbMigrationStatusProvider}
 * (camunda/product-hub#3067), constructed directly against a live {@link BrokerClient} rather than
 * through the {@code upgradeReadiness} actuator endpoint.
 */
@ZeebeIntegration
final class ClusterRocksDbMigrationStatusProviderIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @TestZeebe
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withBrokersCount(3)
          .withPartitionsCount(3)
          .withReplicationFactor(3)
          // Inter-broker replication and the RocksDB provider's fan-out both go over the internal
          // API. Relying on each broker's auto-detected LAN address makes that depend on the
          // environment's network/firewall setup; loopback is always valid for a local cluster.
          .withBrokerConfig(
              broker ->
                  broker.withClusterConfig(
                      cluster -> {
                        cluster.getNetwork().setAdvertisedHost("127.0.0.1");
                        cluster.getNetwork().getInternalApi().setAdvertisedHost("127.0.0.1");
                      }))
          .build();

  @Test
  void shouldReportMigratedAcrossAllPartitionReplicasIncludingFollowers() {
    final var brokerClient = CLUSTER.anyGateway().bean(BrokerClient.class);
    final var provider =
        new ClusterRocksDbMigrationStatusProvider(brokerClient, PhysicalTenantIds.DEFAULT);

    Awaitility.await("until every partition replica, including followers, reports MIGRATED")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = provider.getMigrationStatus();

              assertThat(status).containsOnlyKeys(DEFAULT_PHYSICAL_TENANT_ID);
              assertThat(status.get(DEFAULT_PHYSICAL_TENANT_ID).state())
                  .isEqualTo(MigrationState.MIGRATED);
            });
  }
}
