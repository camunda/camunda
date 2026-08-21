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

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.qa.util.actuator.UpgradeReadinessActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * End-to-end happy-path coverage for {@code GET /actuator/upgradeReadiness}
 * (camunda/product-hub#3067) with RDBMS as the secondary storage: on a normally-booted, healthy
 * broker — no chained-upgrade or migration scenario involved — every registered condition must
 * settle on {@code MIGRATED} for the default physical tenant, and the endpoint must report the
 * cluster as upgradeable.
 */
@ZeebeIntegration
final class UpgradeReadinessEndpointIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final int PARTITION_COUNT = 3;
  private static final String H2_URL =
      "jdbc:h2:mem:upgrade-readiness-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

  @TestZeebe(partitionCount = PARTITION_COUNT)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withSecondaryStorageType(SecondaryStorageType.rdbms)
          .withClusterConfig(
              cluster -> {
                cluster.setPartitionCount(PARTITION_COUNT);
                // The RocksDB provider broadcasts admin requests to every replica over the
                // internal API, which for this single-node broker means connecting to itself.
                // Advertising the host's auto-detected LAN address can make that self-connect
                // depend on the environment's network/firewall setup; loopback is always valid.
                cluster.getNetwork().setAdvertisedHost("127.0.0.1");
                cluster.getNetwork().getInternalApi().setAdvertisedHost("127.0.0.1");
              })
          .withUnifiedConfig(
              cfg -> {
                final var rdbms = cfg.getData().getSecondaryStorage().getRdbms();
                rdbms.setUrl(H2_URL);
                rdbms.setUsername("sa");
                rdbms.setPassword("");
              });

  @Test
  void shouldReportAllProvidersMigratedForTheDefaultPhysicalTenant() {
    Awaitility.await("until every registered condition settles on MIGRATED for the default tenant")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var response = UpgradeReadinessActuator.of(BROKER).get();

              assertThat(response.physicalTenants()).containsOnlyKeys(DEFAULT_PHYSICAL_TENANT_ID);
              final var defaultTenant = response.physicalTenants().get(DEFAULT_PHYSICAL_TENANT_ID);
              assertThat(defaultTenant)
                  .containsKeys("rdbmsSchemaMigrated", "rocksDbMigrated", "exporterMigrated");
              assertThat(defaultTenant.get("rdbmsSchemaMigrated").state()).isEqualTo("MIGRATED");
              assertThat(defaultTenant.get("rocksDbMigrated").state()).isEqualTo("MIGRATED");
              assertThat(defaultTenant.get("exporterMigrated").state()).isEqualTo("MIGRATED");
              assertThat(response.upgradeable()).isTrue();
            });
  }
}
