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
import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a tenant which keeps its partitions' state in the database gets the state's tables
 * with its own prefix, and that other tenants don't get them.
 *
 * <p>The history's changelog is replaced by a minimal one to keep the test fast; only the state's
 * changelog is the real one.
 */
class LiquibaseSchemaManagerPartitionStateH2Test {

  private static final String PREFIX = "TENANT_";
  private static final String HISTORY_CHANGELOG =
      "db/changelog/rdbms-fast-check/changelog-master.xml";

  private JdbcDataSource dataSource;
  private VendorDatabaseProperties h2Properties;

  @BeforeEach
  void setUp() throws Exception {
    h2Properties = VendorDatabasePropertiesLoader.load("h2");
    dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:partition-state-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    dataSource.setUser("sa");
    dataSource.setPassword("");
  }

  @Test
  void shouldCreateStateTablesForTenantKeepingPartitionState() throws Exception {
    // given
    final var schemaManager = schemaManagerFor(true);

    // when
    schemaManager.initialize();

    // then
    assertThat(exists(PREFIX + "FAST_CHECK_PROBE", "TABLE")).isTrue();
    assertThat(exists(PREFIX + "ZS_PARTITION", "TABLE")).isTrue();
    assertThat(exists(PREFIX + "ZS_DEFAULT", "TABLE")).isTrue();
    assertThat(exists(PREFIX + "ZSV_ELEMENT_PARENT_CHILD", "VIEW")).isTrue();
  }

  @Test
  void shouldNotCreateStateTablesForOtherTenants() throws Exception {
    // given
    final var schemaManager = schemaManagerFor(false);

    // when
    schemaManager.initialize();

    // then
    assertThat(exists(PREFIX + "FAST_CHECK_PROBE", "TABLE")).isTrue();
    assertThat(exists(PREFIX + "ZS_PARTITION", "TABLE")).isFalse();
  }

  private LiquibaseSchemaManager schemaManagerFor(final boolean partitionState) {
    return new LiquibaseSchemaManager(
        new PerTenantSchemaConfig(dataSource, h2Properties, PREFIX, true, null, partitionState),
        "8.11.0") {
      @Override
      protected SpringLiquibase buildRunner() {
        final var runner = super.buildRunner();
        runner.setChangeLog(HISTORY_CHANGELOG);
        return runner;
      }
    };
  }

  private boolean exists(final String name, final String type) throws Exception {
    try (final var connection = dataSource.getConnection();
        final var tables =
            connection.getMetaData().getTables(null, null, name, new String[] {type})) {
      return tables.next();
    }
  }
}
