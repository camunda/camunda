/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.util.LinkedHashMap;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * H2-based test that verifies {@link DefaultRdbmsSchemaManagerRegistry} runs an isolated Liquibase
 * migration per physical tenant: each tenant gets its own datasource and its own table prefix, and
 * the resulting Liquibase tracking tables are visible only in the tenant's own database.
 */
class DefaultRdbmsSchemaManagerRegistryH2Test {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";
  private static final String DB_URL_A = "jdbc:h2:mem:mt-A;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
  private static final String DB_URL_B = "jdbc:h2:mem:mt-B;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

  private JdbcDataSource dataSourceA;
  private JdbcDataSource dataSourceB;

  @BeforeEach
  void setUp() throws Exception {
    dataSourceA = newDataSource(DB_URL_A);
    dataSourceB = newDataSource(DB_URL_B);
    dropAllObjects(dataSourceA);
    dropAllObjects(dataSourceB);
  }

  @AfterEach
  void tearDown() throws Exception {
    dropAllObjects(dataSourceA);
    dropAllObjects(dataSourceB);
  }

  @Test
  void shouldRunIsolatedMigrationPerPhysicalTenant() throws Exception {
    // given
    final var configs = new LinkedHashMap<String, PerTenantSchemaConfig>();
    configs.put(
        TENANT_A,
        new PerTenantSchemaConfig(
            dataSourceA, VendorDatabasePropertiesLoader.load("h2"), "A_", true, null));
    configs.put(
        TENANT_B,
        new PerTenantSchemaConfig(
            dataSourceB, VendorDatabasePropertiesLoader.load("h2"), "B_", true, null));
    final var registry = DefaultRdbmsSchemaManagerRegistry.fromConfigs(configs, "8.10.0");

    // when
    registry.afterPropertiesSet();

    // then - both tenants report initialized
    assertThat(registry.isInitialized(TENANT_A)).isTrue();
    assertThat(registry.isInitialized(TENANT_B)).isTrue();

    // and the prefixed Liquibase tracking tables exist only in their respective databases
    assertThat(tableExists(dataSourceA, "A_DATABASECHANGELOG")).isTrue();
    assertThat(tableExists(dataSourceA, "B_DATABASECHANGELOG")).isFalse();
    assertThat(tableExists(dataSourceB, "B_DATABASECHANGELOG")).isTrue();
    assertThat(tableExists(dataSourceB, "A_DATABASECHANGELOG")).isFalse();
  }

  // ---- helpers ----

  private static JdbcDataSource newDataSource(final String url) {
    final var ds = new JdbcDataSource();
    ds.setURL(url);
    ds.setUser("sa");
    ds.setPassword("");
    return ds;
  }

  private static void dropAllObjects(final JdbcDataSource ds) throws Exception {
    try (final var conn = ds.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
    }
  }

  private static boolean tableExists(final JdbcDataSource ds, final String tableName)
      throws Exception {
    try (final var conn = ds.getConnection()) {
      final var meta = conn.getMetaData();
      try (final var rs =
          meta.getTables(null, null, tableName.toUpperCase(), new String[] {"TABLE"})) {
        return rs.next();
      }
    }
  }
}
