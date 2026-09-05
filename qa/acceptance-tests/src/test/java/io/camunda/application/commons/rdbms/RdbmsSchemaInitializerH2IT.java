/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.db.rdbms.PerTenantSchemaConfig;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsSchemaManagers;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Drives {@link RdbmsSchemaInitializer} through real Liquibase against a real (in-memory) database,
 * over the same {@code RdbmsDataSources} the application builds — so what is asserted here is the
 * outcome of an actual migration rather than of a fake schema manager, which is {@code
 * RdbmsSchemaInitializerTest}'s subject.
 *
 * <p>The scenario it exists for is a <em>terminal</em> tenant beside a serviceable one: startup
 * used to come down with it, and the abort must not trigger while another tenant can be served. The
 * failure is a recorded schema version the running version cannot migrate from, seeded before the
 * initializer runs — the RDBMS analogue of the trick {@code SchemaManagerStartupIT} uses for the
 * search engine, and it costs no container.
 *
 * <p>The retryable counterpart, including background recovery and what a client sees meanwhile,
 * needs a live HTTP surface and lives in {@code
 * PhysicalTenantRdbmsSchemaInitializationIsolationIT}.
 */
@Tag("rdbms")
@Timeout(120)
class RdbmsSchemaInitializerH2IT {

  // destroy() runs in a finally inside the try-with-resources rather than an @AfterEach: a failing
  // assertion must not leave a tenant retrying unboundedly for the rest of the fork, and the tasks
  // have to be stopped before their pools are taken away, not after.

  /**
   * The default tenant carries the root configuration and is synthesized as Elasticsearch unless it
   * is declared, and a node may not mix RDBMS with another storage type — so the healthy tenant
   * here has to be the default one.
   */
  private static final String TENANT_A = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

  private static final String TENANT_B = "tenantb";

  private static final String APPLICATION_VERSION = "8.10.0";

  /** Five minors behind, so the upgrade path is illegal whatever the running version turns out. */
  private static final String UNMIGRATABLE_SCHEMA_VERSION = "8.5.0";

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldMigrateEveryTenantIntoItsOwnDatabase() throws Exception {
    // given
    try (final var tenants = wireTenants(TENANT_A, TENANT_B)) {
      final var initializer = initializerFor(tenants);
      try {
        // when
        initializer.afterPropertiesSet();

        // then - each tenant migrated, and into its own database
        assertThat(initializer.isInitialized(TENANT_A)).isTrue();
        assertThat(initializer.isInitialized(TENANT_B)).isTrue();
        assertThat(tableExists(tenants.dataSourceFor(TENANT_A), "DATABASECHANGELOG")).isTrue();
        assertThat(tableExists(tenants.dataSourceFor(TENANT_B), "DATABASECHANGELOG")).isTrue();
      } finally {
        initializer.destroy();
      }
    }
  }

  @Test
  void shouldNotAbortStartupWhenOneTenantCannotBeMigrated() throws Exception {
    // given - tenant B records a schema version the running version cannot migrate from, which is
    // terminal: no retry repairs it, and it used to take the whole context down
    try (final var tenants = wireTenants(TENANT_A, TENANT_B)) {
      seedSchemaVersion(tenants.dataSourceFor(TENANT_B), UNMIGRATABLE_SCHEMA_VERSION);
      final var initializer = initializerFor(tenants);
      try {
        // when / then - the node comes up on the strength of the tenant it can serve
        assertThatCode(initializer::afterPropertiesSet).doesNotThrowAnyException();
        assertThat(initializer.isInitialized(TENANT_A)).isTrue();
        assertThat(initializer.isInitialized(TENANT_B)).isFalse();

        // and - the healthy tenant's migration really ran; it was not merely reported ready
        assertThat(tableExists(tenants.dataSourceFor(TENANT_A), "DATABASECHANGELOG")).isTrue();
        assertThat(tableExists(tenants.dataSourceFor(TENANT_B), "DATABASECHANGELOG")).isFalse();
      } finally {
        initializer.destroy();
      }
    }
  }

  @Test
  void shouldFailStartupWhenTheOnlyTenantCannotBeMigrated() throws Exception {
    // given - the same failure on a single-tenant node, which stays on the synchronous path
    try (final var tenants = wireTenants(TENANT_A)) {
      seedSchemaVersion(tenants.dataSourceFor(TENANT_A), UNMIGRATABLE_SCHEMA_VERSION);
      final var initializer = initializerFor(tenants);
      try {
        // when / then - there is nothing to isolate it from, so it still aborts, as it always has
        assertThatThrownBy(initializer::afterPropertiesSet)
            .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class)
            .hasMessageContaining(UNMIGRATABLE_SCHEMA_VERSION);
        assertThat(initializer.isInitialized(TENANT_A)).isFalse();
      } finally {
        initializer.destroy();
      }
    }
  }

  // ---- helpers ----

  private static RdbmsSchemaInitializer initializerFor(final RdbmsDataSources tenants) {
    return new RdbmsSchemaInitializer(schemaManagersFor(tenants));
  }

  private static Map<String, RdbmsSchemaManager> schemaManagersFor(final RdbmsDataSources tenants) {
    final var configs = new LinkedHashMap<String, PerTenantSchemaConfig>();
    for (final var tenantId : tenants.physicalTenantIds()) {
      configs.put(
          tenantId,
          new PerTenantSchemaConfig(
              tenants.dataSourceFor(tenantId),
              tenants.vendorPropertiesFor(tenantId),
              "",
              /* autoDdl= */ true,
              null));
    }
    // an explicit version rather than the build's own, so that what "cannot be migrated from"
    // means here does not move with the release
    return RdbmsSchemaManagers.fromConfigs(configs, APPLICATION_VERSION);
  }

  private static RdbmsDataSources wireTenants(final String... tenantIds) throws Exception {
    final var props = new LinkedHashMap<String, Object>();
    for (final var tenantId : tenantIds) {
      final var base = "camunda.physical-tenants." + tenantId + ".data.secondary-storage.";
      props.put(base + "type", "rdbms");
      props.put(
          base + "rdbms.url",
          "jdbc:h2:mem:schema-init-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
      props.put(base + "rdbms.username", "sa");
      props.put(base + "rdbms.password", "");
      // every explicitly-configured tenant must provide its own initialization block
      props.put(
          "camunda.physical-tenants."
              + tenantId
              + ".security.initialization.default-roles.admin.users[0]",
          tenantId + "-admin");
    }
    final var environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", props));
    final var resolver = PhysicalTenantResolver.of(environment, new Camunda());
    return RdbmsDataSources.of(
        resolver.mapValues(camunda -> camunda.getData().getSecondaryStorage().getRdbms()),
        new SimpleMeterRegistry());
  }

  /**
   * Records a schema version without running the migration that would normally create the table, so
   * that the tenant's first attempt fails on the compatibility check.
   */
  private static void seedSchemaVersion(final DataSource dataSource, final String version)
      throws Exception {
    try (final Connection connection = dataSource.getConnection();
        final var statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE RDBMS_SCHEMA_VERSION (ID INT NOT NULL PRIMARY KEY, VERSION VARCHAR(50))");
      statement.execute(
          "INSERT INTO RDBMS_SCHEMA_VERSION (ID, VERSION) VALUES (1, '" + version + "')");
      // the application's pool runs with autoCommit disabled, so returning the connection without
      // this rolls the seed back and the tenant migrates as if nothing had been recorded
      connection.commit();
    }
  }

  private static boolean tableExists(final DataSource dataSource, final String tableName)
      throws Exception {
    try (final Connection connection = dataSource.getConnection();
        final var tables =
            connection
                .getMetaData()
                .getTables(null, null, tableName.toUpperCase(), new String[] {"TABLE"})) {
      return tables.next();
    }
  }
}
