/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

/**
 * Regression test for {@link RdbmsSchemaVersionStore#tableExists} across identifier casings.
 *
 * <p>The {@code RDBMS_SCHEMA_VERSION} table is created via an unquoted Liquibase identifier, and
 * unquoted identifiers fold differently per vendor: H2 stores them upper case, but PostgreSQL
 * stores them lower case. A lookup that only tries upper case (or the as-passed literal, which is
 * itself upper case here) never finds the table on a database that folds to lower case, wrongly
 * concluding the schema is fresh even though it is fully migrated.
 *
 * <p>H2 folds unquoted identifiers to upper case regardless of the connection, so a real
 * lower-case-folding vendor cannot be reproduced by creating the table normally. A <em>quoted</em>
 * identifier lets H2 preserve the exact case given, which is enough to simulate the vendor
 * difference for {@code tableExists} without needing a real PostgreSQL instance.
 */
class RdbmsSchemaVersionStoreCasingH2Test {

  private JdbcDataSource newDataSource() {
    final var ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:schema-version-casing-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    return ds;
  }

  @Test
  void shouldFindTableStoredInLowerCase() throws Exception {
    // given: a table stored lower case, as PostgreSQL would fold it
    final var ds = newDataSource();
    try (final var conn = ds.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE \"rdbms_schema_version\" (version VARCHAR(255))");
    }
    final var store = new RdbmsSchemaVersionStore(ds, "", "8.10.0");

    // when / then
    try (final var conn = ds.getConnection()) {
      assertThat(store.tableExists(conn, "RDBMS_SCHEMA_VERSION")).isTrue();
    }
  }

  @Test
  void shouldFindTableStoredInUpperCase() throws Exception {
    // given: a table stored upper case, as H2 folds it by default
    final var ds = newDataSource();
    try (final var conn = ds.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE RDBMS_SCHEMA_VERSION (VERSION VARCHAR(255))");
    }
    final var store = new RdbmsSchemaVersionStore(ds, "", "8.10.0");

    // when / then
    try (final var conn = ds.getConnection()) {
      assertThat(store.tableExists(conn, "RDBMS_SCHEMA_VERSION")).isTrue();
    }
  }

  @Test
  void shouldNotFindTableThatDoesNotExist() throws Exception {
    // given: no table created at all
    final var ds = newDataSource();

    final var store = new RdbmsSchemaVersionStore(ds, "", "8.10.0");

    // when / then
    try (final var conn = ds.getConnection()) {
      assertThat(store.tableExists(conn, "RDBMS_SCHEMA_VERSION")).isFalse();
    }
  }
}
