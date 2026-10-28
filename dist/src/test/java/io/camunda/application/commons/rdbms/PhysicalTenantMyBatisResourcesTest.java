/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.commons.pt.PhysicalTenantResourceUnavailableException;
import io.camunda.configuration.data.secondarystorage.Rdbms;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicalTenantMyBatisResourcesTest {

  private final String tenantA = "tenant-a-" + UUID.randomUUID();
  private final String tenantB = "tenant-b-" + UUID.randomUUID();
  private final Map<String, Rdbms> configs = new LinkedHashMap<>();

  private RdbmsDataSources dataSources;
  private PhysicalTenantMyBatisResources resources;

  @AfterEach
  void tearDown() {
    if (resources != null) {
      resources.close();
    }
    if (dataSources != null) {
      dataSources.close();
    }
  }

  @Test
  void shouldNotBuildAnyTenantsMyBatisOrPoolBeforeItIsUsed() throws Exception {
    // given
    configs.put(tenantA, h2(tenantA));
    configs.put(tenantB, h2(tenantB));

    // when
    create();
    resources.mapperBundleFor(tenantA);
    resources.mapperBundleFor(tenantB);

    // then
    assertThat(resources.isCreated(tenantA)).isFalse();
    assertThat(resources.isCreated(tenantB)).isFalse();
    assertThat(dataSources.isPoolStarted(tenantA)).isFalse();
    assertThat(dataSources.isPoolStarted(tenantB)).isFalse();
  }

  @Test
  void shouldBuildOnlyTheUsedTenantsMyBatisAndPoolOnItsFirstStatement() throws Exception {
    // given
    configs.put(tenantA, h2(tenantA));
    configs.put(tenantB, h2(tenantB));
    create();
    final var bundleA = resources.mapperBundleFor(tenantA);

    // when
    final var rowCount =
        bundleA.tableMetricsMapper().countSingleTableRows("INFORMATION_SCHEMA.USERS");

    // then
    assertThat(rowCount).isPositive();
    assertThat(resources.isCreated(tenantA)).isTrue();
    assertThat(dataSources.isPoolStarted(tenantA)).isTrue();
    assertThat(resources.isCreated(tenantB)).isFalse();
    assertThat(dataSources.isPoolStarted(tenantB)).isFalse();
  }

  @Test
  void shouldBuildATenantsMyBatisAtOnceWhenItOptsOutOfLazyInitialization() throws Exception {
    // given
    final var eager = h2(tenantA);
    eager.setLazyInitialization(false);
    configs.put(tenantA, eager);
    configs.put(tenantB, h2(tenantB));

    // when
    create();

    // then
    assertThat(resources.isCreated(tenantA)).isTrue();
    assertThat(resources.isCreated(tenantB)).isFalse();
    assertThat(dataSources.isPoolStarted(tenantA)).isFalse();
  }

  @Test
  void shouldFailOnlyTheStatementsOfATenantWhoseDatabaseIsUnreachable() throws Exception {
    // given
    final var unreachable = new Rdbms();
    unreachable.setUrl("jdbc:postgresql://localhost:1/camunda");
    unreachable.setUsername("camunda");
    unreachable.setPassword("camunda");
    unreachable.getConnectionPool().setConnectionTimeout(Duration.ofMillis(250));
    configs.put(tenantA, unreachable);
    configs.put(tenantB, h2(tenantB));
    create();

    // when
    final var bundleA = resources.mapperBundleFor(tenantA);
    final var bundleB = resources.mapperBundleFor(tenantB);

    // then
    assertThatThrownBy(() -> bundleA.tableMetricsMapper().countSingleTableRows("PROCESS_INSTANCE"))
        .hasStackTraceContaining("localhost:1");
    assertThat(bundleB.tableMetricsMapper().countSingleTableRows("INFORMATION_SCHEMA.USERS"))
        .isPositive();
  }

  @Test
  void shouldRejectStatementsOnceClosed() throws Exception {
    // given
    configs.put(tenantA, h2(tenantA));
    create();
    final var bundleA = resources.mapperBundleFor(tenantA);

    // when
    resources.close();

    // then
    assertThatThrownBy(() -> bundleA.tableMetricsMapper().countSingleTableRows("X"))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(PhysicalTenantResourceUnavailableException.class);
  }

  private void create() throws Exception {
    dataSources = RdbmsDataSources.of(configs, new SimpleMeterRegistry());
    resources = PhysicalTenantMyBatisResources.create(dataSources, configs::get);
  }

  private static Rdbms h2(final String name) {
    final var rdbms = new Rdbms();
    rdbms.setUrl("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    rdbms.setUsername("sa");
    rdbms.setPassword("");
    return rdbms;
  }
}
