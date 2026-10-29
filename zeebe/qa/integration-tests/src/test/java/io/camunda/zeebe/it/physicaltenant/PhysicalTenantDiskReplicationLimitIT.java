/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.system.monitoring.DiskUsageScanner;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

final class PhysicalTenantDiskReplicationLimitIT {

  private static final String LIMITED_TENANT = "limited";
  private static final MemberId LIMITED_BROKER = MemberId.from("2");
  private static final int BROKERS_COUNT = 3;
  private static final DataSize SEGMENT_SIZE = DataSize.ofMegabytes(4);
  private static final DataSize REPLICATION_LIMIT = DataSize.ofMegabytes(24);
  private static final DataSize FILL = DataSize.ofMegabytes(40);
  private static final String PAYLOAD = "x".repeat(256 * 1024);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(LIMITED_TENANT, Storage.none())
          .build();

  @TempDir private Path dataRoot;
  private TestCluster cluster;
  private CamundaClient defaultClient;
  private CamundaClient limitedClient;

  @BeforeEach
  void beforeEach() {
    cluster =
        new TestClusterBuilder()
            .withBrokersCount(BROKERS_COUNT)
            .withReplicationFactor(BROKERS_COUNT)
            .withPartitionsCount(1)
            .withEmbeddedGateway(true)
            .withBrokerConfig(this::configureBroker)
            .build()
            .start()
            .awaitCompleteTopology();
    TENANTS.awaitTopologyComplete(
        cluster.availableGateway(), LIMITED_TENANT, BROKERS_COUNT, 1, BROKERS_COUNT);
    defaultClient =
        TENANTS
            .newClientBuilder(cluster.availableGateway(), PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
            .build();
    limitedClient = TENANTS.newClientBuilder(cluster.availableGateway(), LIMITED_TENANT).build();
  }

  @AfterEach
  void afterEach() {
    if (defaultClient != null) {
      defaultClient.close();
    }
    if (limitedClient != null) {
      limitedClient.close();
    }
    if (cluster != null) {
      cluster.close();
    }
  }

  @Test
  void shouldStopReplicatingOnlyTheTenantAboveItsReplicationLimit() {
    // when
    fill(limitedClient, LIMITED_TENANT);
    fill(defaultClient, PhysicalTenantsITHelper.DEFAULT_TENANT_ID);

    // then
    assertThat(usedSpace(LIMITED_BROKER, LIMITED_TENANT))
        .isLessThanOrEqualTo(REPLICATION_LIMIT.toBytes());
    await("the default tenant keeps replicating to the broker past the limit of the other tenant")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(usedSpace(LIMITED_BROKER, PhysicalTenantsITHelper.DEFAULT_TENANT_ID))
                    .isGreaterThan(REPLICATION_LIMIT.toBytes()));
  }

  private void configureBroker(final MemberId memberId, final TestStandaloneBroker broker) {
    TENANTS.configure(
        broker
            .withUnauthenticatedAccess()
            .withDataConfig(
                data -> {
                  data.getPrimaryStorage().setDirectory(dataDirectoryOf(memberId).toString());
                  data.getPrimaryStorage().getLogStream().setLogSegmentSize(SEGMENT_SIZE);
                  data.getPrimaryStorage().getDisk().setMonitoringInterval(Duration.ofMillis(100));
                }));
    if (LIMITED_BROKER.equals(memberId)) {
      broker.withPtConfig(
          LIMITED_TENANT,
          camunda ->
              camunda
                  .getData()
                  .getPrimaryStorage()
                  .getDisk()
                  .getUsedSpace()
                  .setReplication(REPLICATION_LIMIT));
    }
  }

  private void fill(final CamundaClient client, final String tenantId) {
    await("the leaders of tenant '%s' hold more data than the limit".formatted(tenantId))
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofMillis(10))
        .until(
            () -> {
              client
                  .newPublishMessageCommand()
                  .messageName("fill")
                  .correlationKey(UUID.randomUUID().toString())
                  .variables(Map.of("payload", PAYLOAD))
                  .send()
                  .join();
              return cluster.brokers().keySet().stream()
                  .filter(memberId -> !LIMITED_BROKER.equals(memberId))
                  .allMatch(memberId -> usedSpace(memberId, tenantId) > FILL.toBytes());
            });
  }

  private long usedSpace(final MemberId memberId, final String tenantId) {
    return new DiskUsageScanner(List.of(dataDirectoryOf(memberId).resolve(tenantId))).getAsLong();
  }

  private Path dataDirectoryOf(final MemberId memberId) {
    return dataRoot.resolve("broker-" + memberId.id());
  }
}
