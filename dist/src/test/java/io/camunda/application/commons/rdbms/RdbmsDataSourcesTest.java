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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.configuration.Rdbms;
import io.camunda.configuration.RdbmsConnectionPool;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RdbmsDataSourcesTest {

  private static final RdbmsDatabaseIdProvider DATABASE_ID_PROVIDER =
      new RdbmsDatabaseIdProvider(null);

  private static Rdbms h2Rdbms() {
    final var rdbms = new Rdbms();
    rdbms.setUrl(
        "jdbc:h2:mem:rdbms-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    rdbms.setUsername("sa");
    rdbms.setPassword("");
    return rdbms;
  }

  @Test
  void shouldBuildDataSourceForSinglePhysicalTenant() throws Exception {
    final var rdbms = h2Rdbms();
    try (final var registry =
        RdbmsDataSources.of(
            Map.of(RdbmsDataSources.DEFAULT_PHYSICAL_TENANT_ID, rdbms), DATABASE_ID_PROVIDER)) {

      // then
      final var ds =
          (HikariDataSource) registry.dataSourceFor(RdbmsDataSources.DEFAULT_PHYSICAL_TENANT_ID);
      assertThat(ds.getJdbcUrl()).isEqualTo(rdbms.getUrl());
      assertThat(ds.getUsername()).isEqualTo("sa");
      assertThat(ds.getDriverClassName()).isEqualTo("org.h2.Driver");
      assertThat(ds.getPoolName()).isEqualTo("camunda-rdbms-default");
    }
  }

  @Test
  void shouldApplyConnectionPoolSettings() throws Exception {
    final var rdbms = h2Rdbms();
    final var pool = new RdbmsConnectionPool();
    pool.setMaximumPoolSize(42);
    pool.setMinimumIdle(7);
    pool.setConnectionTimeout(Duration.ofMillis(1234));
    pool.setIdleTimeout(Duration.ofMillis(45_678));
    pool.setMaxLifetime(Duration.ofMillis(99_999));
    pool.setLeakDetectionThreshold(Duration.ofMillis(2_500));
    rdbms.setConnectionPool(pool);

    try (final var registry =
        RdbmsDataSources.of(
            Map.of(RdbmsDataSources.DEFAULT_PHYSICAL_TENANT_ID, rdbms), DATABASE_ID_PROVIDER)) {

      final var ds =
          (HikariDataSource) registry.dataSourceFor(RdbmsDataSources.DEFAULT_PHYSICAL_TENANT_ID);
      assertThat(ds.getMaximumPoolSize()).isEqualTo(42);
      assertThat(ds.getMinimumIdle()).isEqualTo(7);
      assertThat(ds.getConnectionTimeout()).isEqualTo(1234);
      assertThat(ds.getIdleTimeout()).isEqualTo(45_678);
      assertThat(ds.getMaxLifetime()).isEqualTo(99_999);
      assertThat(ds.getLeakDetectionThreshold()).isEqualTo(2_500);
    }
  }

  @Test
  void shouldDetectVendorPropertiesPerPhysicalTenant() throws Exception {
    final var configs = new LinkedHashMap<String, Rdbms>();
    configs.put("tenant-a", h2Rdbms());
    configs.put("tenant-b", h2Rdbms());

    try (final var registry = RdbmsDataSources.of(configs, DATABASE_ID_PROVIDER)) {
      assertThat(registry.dataSourceFor("tenant-a")).isNotNull();
      assertThat(registry.dataSourceFor("tenant-b")).isNotNull();
      assertThat(registry.vendorPropertiesFor("tenant-a")).isNotNull();
      assertThat(registry.vendorPropertiesFor("tenant-b")).isNotNull();
    }
  }

  @Test
  void shouldThrowWhenLookingUpUnknownPhysicalTenantDataSource() throws Exception {
    try (final var registry =
        RdbmsDataSources.of(
            Map.of(RdbmsDataSources.DEFAULT_PHYSICAL_TENANT_ID, h2Rdbms()), DATABASE_ID_PROVIDER)) {
      assertThatThrownBy(() -> registry.dataSourceFor("missing"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("missing");
    }
  }

  @Test
  void shouldThrowWhenLookingUpUnknownPhysicalTenantVendorProperties() throws Exception {
    try (final var registry =
        RdbmsDataSources.of(
            Map.of(RdbmsDataSources.DEFAULT_PHYSICAL_TENANT_ID, h2Rdbms()), DATABASE_ID_PROVIDER)) {
      assertThatThrownBy(() -> registry.vendorPropertiesFor("missing"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("missing");
    }
  }

  @Test
  void shouldCloseAllDataSourcesOnClose() throws Exception {
    final var configs = new LinkedHashMap<String, Rdbms>();
    configs.put("tenant-a", h2Rdbms());
    configs.put("tenant-b", h2Rdbms());

    final HikariDataSource dsA;
    final HikariDataSource dsB;
    try (final var registry = RdbmsDataSources.of(configs, DATABASE_ID_PROVIDER)) {
      dsA = (HikariDataSource) registry.dataSourceFor("tenant-a");
      dsB = (HikariDataSource) registry.dataSourceFor("tenant-b");
      assertThat(dsA.isClosed()).isFalse();
      assertThat(dsB.isClosed()).isFalse();
    }

    assertThat(dsA.isClosed()).isTrue();
    assertThat(dsB.isClosed()).isTrue();
  }

  @Test
  void shouldCloseAlreadyOpenedDataSourcesWhenLaterTenantFails() throws Exception {
    final var configs = new LinkedHashMap<String, Rdbms>();
    configs.put("tenant-a", h2Rdbms());
    configs.put("tenant-b", h2Rdbms());

    // tenant-a → real H2 detection succeeds; tenant-b → throws during databaseId resolution.
    final var capturingProvider = spy(new RdbmsDatabaseIdProvider(null));
    doCallRealMethod()
        .doThrow(new RuntimeException("boom on tenant-b"))
        .when(capturingProvider)
        .getDatabaseId(any(DataSource.class));

    assertThatThrownBy(() -> RdbmsDataSources.of(configs, capturingProvider))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("boom on tenant-b");

    final var captor = ArgumentCaptor.forClass(DataSource.class);
    verify(capturingProvider, times(2)).getDatabaseId(captor.capture());
    for (final var ds : captor.getAllValues()) {
      assertThat(((HikariDataSource) ds).isClosed())
          .as("opened HikariDataSource should be closed on failure")
          .isTrue();
    }
  }
}
