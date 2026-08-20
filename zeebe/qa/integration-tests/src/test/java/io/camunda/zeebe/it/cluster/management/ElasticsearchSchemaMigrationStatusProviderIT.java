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
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end happy-path coverage for {@code GET /actuator/upgradeReadiness}
 * (camunda/product-hub#3067) with Elasticsearch as the secondary storage: a normally-booted broker
 * initializes its own schema at startup (via the existing search-engine schema initializer), which
 * records the current application version — so the {@code elasticsearchSchemaMigrated} condition
 * must settle on {@code MIGRATED} for the default physical tenant without any exporter, chained
 * upgrade, or migration scenario involved.
 *
 * <p>Sibling of {@link UpgradeReadinessEndpointIT} (RDBMS): the two secondary storage types are
 * mutually exclusive per broker, so this is a separate broker/test rather than an extension of that
 * one.
 */
@Testcontainers
@ZeebeIntegration
final class ElasticsearchSchemaMigrationStatusProviderIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefaultElasticsearchContainer();

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker().withSecondaryStorageType(SecondaryStorageType.elasticsearch);

  @BeforeEach
  void beforeEach() {
    broker
        // TestStandaloneBroker disables schema creation by default (ES/OS containers may not be
        // used in a given test); this IT needs the real schema (and its schema-version metadata
        // document) actually created at startup.
        .withCreateSchema(true)
        .withUnifiedConfig(
            cfg ->
                cfg.getData()
                    .getSecondaryStorage()
                    .getElasticsearch()
                    .setUrl("http://" + CONTAINER.getHttpHostAddress()));
    broker.start();
  }

  @Test
  void shouldReportElasticsearchSchemaMigratedForTheDefaultPhysicalTenant() {
    Awaitility.await(
            "until the elasticsearchSchemaMigrated condition settles on MIGRATED for the default"
                + " tenant")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var response = UpgradeReadinessActuator.of(broker).get();

              assertThat(response.physicalTenants()).containsOnlyKeys(DEFAULT_PHYSICAL_TENANT_ID);
              final var defaultTenant = response.physicalTenants().get(DEFAULT_PHYSICAL_TENANT_ID);
              assertThat(defaultTenant).containsKey("elasticsearchSchemaMigrated");
              assertThat(defaultTenant.get("elasticsearchSchemaMigrated").state())
                  .isEqualTo("MIGRATED");
              assertThat(response.upgradeable()).isTrue();
            });
  }
}
