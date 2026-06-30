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
 * Regression test for cross-schema leakage in {@link RdbmsSchemaVersionStore#tableExists}.
 *
 * <p>On a shared server hosting multiple physical tenants (one schema/database per PT), the
 * table-existence lookup must be scoped to the connection's own catalog/schema. Otherwise a PT
 * whose schema is still empty would find another PT's {@code RDBMS_SCHEMA_VERSION} table, wrongly
 * conclude its schema already exists, and then fail the subsequent {@code SELECT} against its own
 * (empty) schema. Two schemas in a single H2 database reproduce that shared-server topology.
 */
class RdbmsSchemaVersionStoreSchemaScopingH2Test {

  private JdbcDataSource newDataSource() {
    final var ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:pt-scoping-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    return ds;
  }

  @Test
  void shouldNotSeeSchemaVersionTableFromAnotherSchema() throws Exception {
    // given: two schemas in one DB; RDBMS_SCHEMA_VERSION exists only in PT_A
    final var ds = newDataSource();
    try (final var conn = ds.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA PT_A");
      stmt.execute("CREATE SCHEMA PT_B");
      stmt.execute("CREATE TABLE PT_A.RDBMS_SCHEMA_VERSION (VERSION VARCHAR(255))");
      stmt.execute("INSERT INTO PT_A.RDBMS_SCHEMA_VERSION (VERSION) VALUES ('8.10.0')");
    }
    final var store = new RdbmsSchemaVersionStore(ds, "", "8.10.0");

    // when: resolving the version while connected to the still-empty PT_B schema
    final String resolved;
    try (final var conn = ds.getConnection()) {
      conn.setSchema("PT_B");
      resolved = store.resolveCurrentSchemaVersion(conn, "");
    }

    // then: PT_B is treated as a fresh database, not PT_A's version
    assertThat(resolved).isNull();
  }

  @Test
  void shouldSeeSchemaVersionTableInOwnSchema() throws Exception {
    // given: RDBMS_SCHEMA_VERSION exists in PT_A
    final var ds = newDataSource();
    try (final var conn = ds.getConnection();
        final var stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA PT_A");
      stmt.execute("CREATE TABLE PT_A.RDBMS_SCHEMA_VERSION (VERSION VARCHAR(255))");
      stmt.execute("INSERT INTO PT_A.RDBMS_SCHEMA_VERSION (VERSION) VALUES ('8.10.0')");
    }
    final var store = new RdbmsSchemaVersionStore(ds, "", "8.10.0");

    // when: resolving the version while connected to PT_A
    final String resolved;
    try (final var conn = ds.getConnection()) {
      conn.setSchema("PT_A");
      resolved = store.resolveCurrentSchemaVersion(conn, "");
    }

    // then: the version stored in PT_A is read back
    assertThat(resolved).isEqualTo("8.10.0");
  }
}
