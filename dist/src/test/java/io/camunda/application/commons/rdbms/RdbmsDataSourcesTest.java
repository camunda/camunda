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
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class RdbmsDataSourcesTest {

  /**
   * A url naming no vendor this application ships properties for. jTDS and the proxy wrappers are
   * the real instances; a made-up prefix stands in for them so that the test does not need their
   * drivers on the classpath, and nothing is listening at the address either way.
   */
  private static final String UNRECOGNIZED_URL = "jdbc:unmapped-proxy://localhost:1/camunda";

  private static Rdbms unrecognizedUrlRdbms() {
    final var rdbms = new Rdbms();
    rdbms.setUrl(UNRECOGNIZED_URL);
    rdbms.setUsername("camunda");
    rdbms.setPassword("camunda");
    return rdbms;
  }

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

  static Stream<Arguments> vendorsResolvableFromTheirUrl() {
    return Stream.of(
        Arguments.of("h2", "jdbc:h2:mem:camunda", "h2"),
        Arguments.of("postgresql", "jdbc:postgresql://localhost:5432/camunda", "postgresql"),
        Arguments.of("oracle", "jdbc:oracle:thin:@localhost:1521/camunda", "oracle"),
        Arguments.of("mariadb", "jdbc:mariadb://localhost:3306/camunda", "mariadb"),
        Arguments.of("mysql", "jdbc:mysql://localhost:3306/camunda", "mysql"),
        Arguments.of("mssql", "jdbc:sqlserver://localhost:1433;databaseName=camunda", "mssql"),
        Arguments.of(
            "postgresql behind the AWS JDBC wrapper",
            "jdbc:aws-wrapper:postgresql://localhost:5432/camunda",
            "postgresql"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("vendorsResolvableFromTheirUrl")
  void shouldResolveVendorPropertiesWithoutReachingTheDatabase(
      final String vendorName, final String url, final String expectedDatabaseId) throws Exception {
    // given - a physical tenant whose database is not listening on that address at all
    final var rdbms = new Rdbms();
    rdbms.setUrl(url);
    rdbms.setUsername("camunda");
    rdbms.setPassword("camunda");

    // when
    try (final var registry =
        RdbmsDataSources.of(
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, rdbms),
            new SimpleMeterRegistry())) {

      // then - the registry is built anyway, which is what lets one tenant's outage degrade that
      // tenant alone instead of failing the whole context refresh
      assertThat(
              registry
                  .vendorPropertiesFor(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                  .databaseId())
          .isEqualTo(expectedDatabaseId);
    }
  }

  @Test
  void shouldWarnWhenATenantsVendorHasToBeReadFromItsDatabaseBesideOtherTenants() {
    // given - two tenants, one behind a url this application cannot classify, with nothing
    // listening at that address either
    final var configs = new LinkedHashMap<String, Rdbms>();
    configs.put(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, unrecognizedUrlRdbms());
    configs.put("tenant-b", h2Rdbms());

    try (final var logs = LogCapturer.capturing(RdbmsDataSources.class, Level.DEBUG)) {
      // when / then - a configuration error rather than a degraded tenant: without a vendor id
      // there is no SqlSessionFactory, and so no tenant to degrade
      assertThatThrownBy(() -> RdbmsDataSources.of(configs, new SimpleMeterRegistry()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot determine the database vendor")
          .hasMessageContaining(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
          .hasMessageContaining(UNRECOGNIZED_URL)
          .hasMessageContaining(RdbmsVendorIdProvider.VENDOR_ID_PROPERTY)
          .hasMessageContaining("h2, postgresql, oracle, mariadb, mysql, mssql");

      // and - the warning is what tells the operator that this one tenant now decides whether
      // every tenant on the node starts
      assertThat(logs.messagesAt(Level.WARN))
          .anySatisfy(
              event ->
                  assertThat(event)
                      .contains(RdbmsVendorIdProvider.VENDOR_ID_PROPERTY)
                      .contains("fails startup for every physical tenant"));
    }
  }

  @Test
  void shouldNotWarnAboutReadingTheVendorFromTheDatabaseOnASingleTenantNode() {
    // given - the same deployment with nobody to take down but itself
    try (final var logs = LogCapturer.capturing(RdbmsDataSources.class, Level.DEBUG)) {

      // when
      assertThatThrownBy(
              () ->
                  RdbmsDataSources.of(
                      Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, unrecognizedUrlRdbms()),
                      new SimpleMeterRegistry()))
          .isInstanceOf(IllegalArgumentException.class);

      // then - still reported, but at a level that does not fire on every start of a deployment
      // with no isolation to lose
      assertThat(logs.messagesAt(Level.WARN)).isEmpty();
      assertThat(logs.messagesAt(Level.DEBUG))
          .anySatisfy(
              event -> assertThat(event).contains(RdbmsVendorIdProvider.VENDOR_ID_PROPERTY));
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
            true,
            "rewriteBatchedStatements",
            "true"),
        new BatchRewritingCase(
            "mariadb enabled by default",
            "jdbc:mariadb://localhost:3306/testdb",
            true,
            "useBulkStmts",
            "true"),
        new BatchRewritingCase(
            "mysql disabled via config",
            "jdbc:mysql://localhost:3306/testdb",
            false,
            "rewriteBatchedStatements",
            null),
        new BatchRewritingCase(
            "mysql behind AWS JDBC wrapper",
            "jdbc:aws-wrapper:mysql://localhost:3306/testdb",
            true,
            "rewriteBatchedStatements",
            "true"),
        new BatchRewritingCase(
            "mariadb behind AWS JDBC wrapper",
            "jdbc:aws-wrapper:mariadb://localhost:3306/testdb",
            true,
            "useBulkStmts",
            "true"),
        new BatchRewritingCase(
            "mysql url already sets the property explicitly",
            "jdbc:mysql://localhost:3306/testdb?rewriteBatchedStatements=false",
            true,
            "rewriteBatchedStatements",
            null),
        new BatchRewritingCase(
            "mariadb url already sets the property explicitly",
            "jdbc:mariadb://localhost:3306/testdb?useBulkStmts=false",
            true,
            "useBulkStmts",
            null),
        new BatchRewritingCase(
            "mysql url already sets the property explicitly to true",
            "jdbc:mysql://localhost:3306/testdb?rewriteBatchedStatements=true",
            true,
            "rewriteBatchedStatements",
            null),
        new BatchRewritingCase(
            "mariadb url already sets the property explicitly to true",
            "jdbc:mariadb://localhost:3306/testdb?useBulkStmts=true",
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
        Arguments.of("h2", h2Rdbms().getUrl()),
        Arguments.of("postgresql", "jdbc:postgresql://localhost:5432/testdb"),
        Arguments.of("oracle", "jdbc:oracle:thin:@localhost:1521/testdb"),
        Arguments.of("mssql", "jdbc:sqlserver://localhost:1433;databaseName=testdb"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("otherVendors")
  void shouldNotSetBatchRewritingPropertyForOtherVendors(final String vendorName, final String url)
      throws Exception {
    // given
    final var rdbms = new Rdbms();
    rdbms.setUrl(url);
    rdbms.setUsername("sa");
    rdbms.setPassword("");

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
      // RdbmsVendorIdProvider rejects it, triggering the cleanup path.
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
      boolean rewriteBatchedStatements,
      String expectedPropertyName,
      String expectedPropertyValue) {
    @Override
    public String toString() {
      return name;
    }
  }
}
