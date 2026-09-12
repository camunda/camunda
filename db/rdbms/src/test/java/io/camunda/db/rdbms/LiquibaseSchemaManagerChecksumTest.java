/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import io.camunda.db.rdbms.exception.RdbmsSchemaMigrationFailedException;
import java.util.LinkedHashMap;
import liquibase.exception.ValidationFailedException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Covers a changeset edited after it was already applied to the schema. */
class LiquibaseSchemaManagerChecksumTest {

  private static final String DB_URL =
      "jdbc:h2:mem:checksum-mismatch;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
  private static final String PREFIX = "C_";

  private JdbcDataSource dataSource;

  @BeforeEach
  void setUp() {
    dataSource = new JdbcDataSource();
    dataSource.setURL(DB_URL);
    dataSource.setUser("sa");
    dataSource.setPassword("");
  }

  @AfterEach
  void tearDown() throws Exception {
    try (final var conn = dataSource.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
    }
  }

  @Test
  void shouldFailTerminallyWhenAnAppliedChangesetChecksumNoLongerMatches() throws Exception {
    // given - a schema that has been migrated once, so every changeset has a recorded checksum
    final var schemaManager = newSchemaManager();
    schemaManager.initialize();

    // when - a recorded checksum stops matching the changelog, as editing an applied changeset does
    corruptRecordedChecksums();

    // then - terminal, and still naming the Liquibase failure underneath
    assertThatThrownBy(schemaManager::initialize)
        .isInstanceOf(RdbmsSchemaMigrationFailedException.class)
        .hasMessageContaining(PREFIX)
        .hasRootCauseInstanceOf(ValidationFailedException.class);
  }

  @Test
  void shouldNotWrapAFailureThatRetryingCouldRepair() throws Exception {
    // given - a schema whose changelog applies cleanly
    final var schemaManager = newSchemaManager();

    // when / then - a healthy run is unaffected by the classification
    schemaManager.initialize();
    assertThat(tableExists(PREFIX + "DATABASECHANGELOG")).isTrue();
  }

  private RdbmsSchemaManager newSchemaManager() throws Exception {
    final var configs = new LinkedHashMap<String, PerTenantSchemaConfig>();
    configs.put(
        "tenant",
        new PerTenantSchemaConfig(
            dataSource, VendorDatabasePropertiesLoader.load("h2"), PREFIX, true, null));
    return RdbmsSchemaManagers.fromConfigs(configs, "8.10.0").values().iterator().next();
  }

  private void corruptRecordedChecksums() throws Exception {
    try (final var conn = dataSource.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute(
          "UPDATE "
              + PREFIX
              + "DATABASECHANGELOG SET MD5SUM = '9:00000000000000000000000000000000'");
    }
  }

  private boolean tableExists(final String tableName) throws Exception {
    try (final var conn = dataSource.getConnection()) {
      try (final var rs =
          conn.getMetaData()
              .getTables(null, null, tableName.toUpperCase(), new String[] {"TABLE"})) {
        return rs.next();
      }
    }
  }
}
