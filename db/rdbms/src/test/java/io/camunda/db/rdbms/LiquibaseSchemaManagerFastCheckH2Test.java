/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.util.UUID;
import liquibase.Scope;
import liquibase.changelog.ChangeLogHistoryServiceFactory;
import liquibase.changelog.FastCheckService;
import liquibase.integration.spring.SpringLiquibase;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that Liquibase's JVM-wide fast-check cache cannot mistake one prefixed physical tenant's
 * schema for another's.
 *
 * <p>The cache key does not contain Liquibase's changelog table name. This test uses a deliberately
 * minimal master changelog because the production changelog contains a changeset that requires an
 * update-command run even after the schema is otherwise current. The real seed changelog is still
 * used so the skipped master migration is reported as initialized, matching #62802.
 */
class LiquibaseSchemaManagerFastCheckH2Test {

  private static final String TENANT_A_PREFIX = "TENANT_A_";
  private static final String TENANT_B_PREFIX = "TENANT_B_";
  private static final String FAST_CHECK_CHANGELOG =
      "db/changelog/rdbms-fast-check/changelog-master.xml";

  private JdbcDataSource dataSource;
  private VendorDatabaseProperties h2Properties;

  @BeforeEach
  void setUp() throws Exception {
    h2Properties = VendorDatabasePropertiesLoader.load("h2");
    dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:fast-check-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    dataSource.setUser("sa");
    dataSource.setPassword("");
  }

  @Test
  @RegressionTest("https://github.com/camunda/camunda/issues/62802")
  void shouldMigratePrefixedTenantWhenAnotherTenantIsCachedAsUpToDate() throws Exception {
    // given a second tenant seeded by a peer, and a first tenant fully migrated by that peer
    final var tenantA = schemaManagerFor(TENANT_A_PREFIX);
    final var tenantB = schemaManagerFor(TENANT_B_PREFIX);
    tenantB.seedSchemaVersion();
    tenantA.initialize();
    resetLiquibaseServicesAfterPeerMigration();

    // when this node sees tenant A is current before it initializes tenant B
    tenantA.initialize();
    tenantB.initialize();

    // then tenant B's master changelog was not skipped because of tenant A's cached result
    assertThat(tableExists(TENANT_B_PREFIX + "FAST_CHECK_PROBE")).isTrue();
  }

  private LiquibaseSchemaManager schemaManagerFor(final String prefix) throws Exception {
    return new LiquibaseSchemaManager(
        new PerTenantSchemaConfig(dataSource, h2Properties, prefix, true, null), "8.10.0") {
      @Override
      protected SpringLiquibase buildRunner() {
        final var runner = super.buildRunner();
        runner.setChangeLog(FAST_CHECK_CHANGELOG);
        return runner;
      }
    };
  }

  private static void resetLiquibaseServicesAfterPeerMigration() {
    final var scope = Scope.getCurrentScope();
    scope.getSingleton(ChangeLogHistoryServiceFactory.class).resetAll();
    scope.getSingleton(FastCheckService.class).clearCache();
  }

  private boolean tableExists(final String tableName) throws Exception {
    try (final var connection = dataSource.getConnection()) {
      final var metadata = connection.getMetaData();
      try (final var tables =
          metadata.getTables(null, null, tableName.toUpperCase(), new String[] {"TABLE"})) {
        return tables.next();
      }
    }
  }
}
