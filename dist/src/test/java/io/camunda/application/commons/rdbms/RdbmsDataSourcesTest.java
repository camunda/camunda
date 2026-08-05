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
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Rdbms;
import io.camunda.configuration.RdbmsConnectionPool;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class RdbmsDataSourcesTest {

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
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, rdbms),
            new SimpleMeterRegistry())) {

      // then
      final var ds =
          (HikariDataSource) registry.dataSourceFor(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
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
    // keepaliveTime must be >= 30s and < maxLifetime or HikariCP resets it to 0 on validation
    pool.setKeepaliveTime(Duration.ofMillis(30_000));
    pool.setValidationTimeout(Duration.ofMillis(3_000));
    rdbms.setConnectionPool(pool);

    try (final var registry =
        RdbmsDataSources.of(
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, rdbms),
            new SimpleMeterRegistry())) {

      final var ds =
          (HikariDataSource) registry.dataSourceFor(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
      assertThat(ds.getMaximumPoolSize()).isEqualTo(42);
      assertThat(ds.getMinimumIdle()).isEqualTo(7);
      assertThat(ds.getConnectionTimeout()).isEqualTo(1234);
      assertThat(ds.getIdleTimeout()).isEqualTo(45_678);
      assertThat(ds.getMaxLifetime()).isEqualTo(99_999);
      assertThat(ds.getLeakDetectionThreshold()).isEqualTo(2_500);
      assertThat(ds.getKeepaliveTime()).isEqualTo(30_000);
      assertThat(ds.getValidationTimeout()).isEqualTo(3_000);
    }
  }

  @Test
  void shouldApplyHikariDefaultsForKeepaliveAndValidationTimeoutWhenUnset() throws Exception {
    // given: a pool with default keepalive (disabled) and validation timeout
    final var rdbms = h2Rdbms();
    rdbms.setConnectionPool(new RdbmsConnectionPool());

    try (final var registry =
        RdbmsDataSources.of(
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, rdbms),
            new SimpleMeterRegistry())) {

      // then: keepalive stays disabled and validation timeout keeps the HikariCP default
      final var ds =
          (HikariDataSource) registry.dataSourceFor(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
      assertThat(ds.getKeepaliveTime()).isZero();
      assertThat(ds.getValidationTimeout()).isEqualTo(5_000);
    }
  }

  @Test
  void shouldDetectVendorPropertiesPerPhysicalTenant() throws Exception {
    final var configs = new LinkedHashMap<String, Rdbms>();
    configs.put("tenant-a", h2Rdbms());
    configs.put("tenant-b", h2Rdbms());

    try (final var registry = RdbmsDataSources.of(configs, new SimpleMeterRegistry())) {
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
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, h2Rdbms()),
            new SimpleMeterRegistry())) {
      assertThatThrownBy(() -> registry.dataSourceFor("missing"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("missing");
    }
  }

  @Test
  void shouldThrowWhenLookingUpUnknownPhysicalTenantVendorProperties() throws Exception {
    try (final var registry =
        RdbmsDataSources.of(
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, h2Rdbms()),
            new SimpleMeterRegistry())) {
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
    try (final var registry = RdbmsDataSources.of(configs, new SimpleMeterRegistry())) {
      dsA = (HikariDataSource) registry.dataSourceFor("tenant-a");
      dsB = (HikariDataSource) registry.dataSourceFor("tenant-b");
      assertThat(dsA.isClosed()).isFalse();
      assertThat(dsB.isClosed()).isFalse();
    }

    assertThat(dsA.isClosed()).isTrue();
    assertThat(dsB.isClosed()).isTrue();
  }

  static Stream<BatchRewritingCase> batchRewritingCases() {
    return Stream.of(
        new BatchRewritingCase(
            "mysql enabled by default",
            "jdbc:mysql://localhost:3306/testdb",
            "mysql",
            true,
            "rewriteBatchedStatements",
            "true"),
        new BatchRewritingCase(
            "mariadb enabled by default",
            "jdbc:mariadb://localhost:3306/testdb",
            "mariadb",
            true,
            "useBulkStmts",
            "true"),
        new BatchRewritingCase(
            "mysql disabled via config",
            "jdbc:mysql://localhost:3306/testdb",
            "mysql",
            false,
            "rewriteBatchedStatements",
            null),
        new BatchRewritingCase(
            "mysql behind AWS JDBC wrapper",
            "jdbc:aws-wrapper:mysql://localhost:3306/testdb",
            "mysql",
            true,
            "rewriteBatchedStatements",
            "true"),
        new BatchRewritingCase(
            "mariadb behind AWS JDBC wrapper",
            "jdbc:aws-wrapper:mariadb://localhost:3306/testdb",
            "mariadb",
            true,
            "useBulkStmts",
            "true"),
        new BatchRewritingCase(
            "mysql url already sets the property explicitly",
            "jdbc:mysql://localhost:3306/testdb?rewriteBatchedStatements=false",
            "mysql",
            true,
            "rewriteBatchedStatements",
            null),
        new BatchRewritingCase(
            "mariadb url already sets the property explicitly",
            "jdbc:mariadb://localhost:3306/testdb?useBulkStmts=false",
            "mariadb",
            true,
            "useBulkStmts",
            null),
        new BatchRewritingCase(
            "mysql url already sets the property explicitly to true",
            "jdbc:mysql://localhost:3306/testdb?rewriteBatchedStatements=true",
            "mysql",
            true,
            "rewriteBatchedStatements",
            null),
        new BatchRewritingCase(
            "mariadb url already sets the property explicitly to true",
            "jdbc:mariadb://localhost:3306/testdb?useBulkStmts=true",
            "mariadb",
            true,
            "useBulkStmts",
            null));
  }

  @ParameterizedTest
  @MethodSource("batchRewritingCases")
  void shouldApplyBatchRewritingProperty(final BatchRewritingCase testCase) throws Exception {
    // given
    final var rdbms = new Rdbms();
    rdbms.setUrl(testCase.url());
    rdbms.setUsername("sa");
    rdbms.setPassword("");
    rdbms.setDatabaseVendorId(testCase.databaseVendorId());
    rdbms.setRewriteBatchedStatements(testCase.rewriteBatchedStatements());

    // when
    try (final var registry =
        RdbmsDataSources.of(
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, rdbms),
            new SimpleMeterRegistry())) {

      // then
      final var ds =
          (HikariDataSource) registry.dataSourceFor(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
      assertThat(ds.getDataSourceProperties().getProperty(testCase.expectedPropertyName()))
          .isEqualTo(testCase.expectedPropertyValue());
    }
  }

  static Stream<Arguments> otherVendors() {
    return Stream.of(
        Arguments.of("h2", h2Rdbms().getUrl(), "h2"),
        Arguments.of("postgresql", "jdbc:postgresql://localhost:5432/testdb", "postgresql"),
        Arguments.of("oracle", "jdbc:oracle:thin:@localhost:1521/testdb", "oracle"),
        Arguments.of("mssql", "jdbc:sqlserver://localhost:1433;databaseName=testdb", "mssql"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("otherVendors")
  void shouldNotSetBatchRewritingPropertyForOtherVendors(
      final String vendorName, final String url, final String databaseVendorId) throws Exception {
    // given
    final var rdbms = new Rdbms();
    rdbms.setUrl(url);
    rdbms.setUsername("sa");
    rdbms.setPassword("");
    rdbms.setDatabaseVendorId(databaseVendorId);

    // when
    try (final var registry =
        RdbmsDataSources.of(
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, rdbms),
            new SimpleMeterRegistry())) {

      // then
      final var ds =
          (HikariDataSource) registry.dataSourceFor(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
      assertThat(ds.getDataSourceProperties().getProperty("rewriteBatchedStatements")).isNull();
      assertThat(ds.getDataSourceProperties().getProperty("useBulkStmts")).isNull();
    }
  }

  @Test
  void shouldCloseAlreadyOpenedDataSourcesWhenLaterTenantFails() {
    try (final MockedStatic<RdbmsDataSources> spy =
        mockStatic(RdbmsDataSources.class, CALLS_REAL_METHODS)) {
      // given: tenant-a initialises normally; tenant-b carries an invalid databaseVendorId so that
      // RdbmsDatabaseIdProvider.getDatabaseId throws, triggering the cleanup path.
      final var tenantBRdbms = h2Rdbms();
      tenantBRdbms.setDatabaseVendorId("unsupported");

      final var configs = new LinkedHashMap<String, Rdbms>();
      configs.put("tenant-a", h2Rdbms());
      configs.put("tenant-b", tenantBRdbms);

      // when / then
      assertThatThrownBy(() -> RdbmsDataSources.of(configs, new SimpleMeterRegistry()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("unsupported");

      final var captor = ArgumentCaptor.forClass(HikariDataSource.class);
      spy.verify(() -> RdbmsDataSources.closeQuietly(captor.capture()), times(2));
      for (final var dataSource : captor.getAllValues()) {
        assertThat(dataSource.isClosed())
            .as("opened HikariDataSource should be closed on failure")
            .isTrue();
      }
    }
  }

  private record BatchRewritingCase(
      String name,
      String url,
      String databaseVendorId,
      boolean rewriteBatchedStatements,
      String expectedPropertyName,
      String expectedPropertyValue) {
    @Override
    public String toString() {
      return name;
    }
  }
}
