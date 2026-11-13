/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Checks the SQL script that operators run to create the state's tables when they turn auto-ddl
 * off, against the changelog that creates them otherwise.
 */
class StateSqlScriptTest {

  private final TestDatabase fromChangelog = createDatabase();
  private final TestDatabase fromScript = createDatabase();

  @AfterEach
  void tearDown() throws SQLException {
    fromChangelog.close();
    fromScript.close();
  }

  @Test
  void shouldCreateTheSameTablesAndViewsAsTheChangelog() throws Exception {
    // given
    TestStorage.createTables(fromChangelog.dataSource(), "");
    final var expected = describeSchema(fromChangelog);
    expected.keySet().removeIf(name -> name.startsWith("DATABASECHANGELOG"));

    // when
    runScript(
        fromScript,
        "liquibase/sql/create/%1$s/%1$s_zeebe_state.sql".formatted(fromScript.databaseId()));

    // then
    assertThat(describeSchema(fromScript)).isNotEmpty().isEqualTo(expected);
  }

  private static void runScript(final TestDatabase database, final String path)
      throws IOException, SQLException {
    final String script;
    try (final var stream = StateSqlScriptTest.class.getClassLoader().getResourceAsStream(path)) {
      script =
          new String(
              Objects.requireNonNull(stream, "No script at " + path).readAllBytes(),
              StandardCharsets.UTF_8);
    }
    for (final var statement : script.replaceAll("--[^\n]*", "").split(";")) {
      if (!statement.isBlank()) {
        database.execute(statement);
      }
    }
  }

  /** The type, columns and primary key of every table and view, by name in upper case. */
  private static Map<String, List<String>> describeSchema(final TestDatabase database)
      throws SQLException {
    final var schema = new TreeMap<String, List<String>>();
    try (final var connection = database.dataSource().getConnection()) {
      final var metaData = connection.getMetaData();
      final var catalog = connection.getCatalog();
      final var schemaName = connection.getSchema();
      try (final var tables =
          metaData.getTables(catalog, schemaName, "%", new String[] {"TABLE", "VIEW"})) {
        while (tables.next()) {
          final var description = new ArrayList<String>();
          description.add(tables.getString("TABLE_TYPE"));
          try (final var primaryKey =
              metaData.getPrimaryKeys(catalog, schemaName, tables.getString("TABLE_NAME"))) {
            while (primaryKey.next()) {
              description.add("PRIMARY KEY " + upperCase(primaryKey.getString("COLUMN_NAME")));
            }
          }
          schema.put(upperCase(tables.getString("TABLE_NAME")), description);
        }
      }
      try (final var columns = metaData.getColumns(catalog, schemaName, "%", "%")) {
        while (columns.next()) {
          final var table = schema.get(upperCase(columns.getString("TABLE_NAME")));
          if (table != null) {
            table.add(
                "%s %s %s"
                    .formatted(
                        upperCase(columns.getString("COLUMN_NAME")),
                        columns.getString("TYPE_NAME"),
                        columns.getString("IS_NULLABLE")));
          }
        }
      }
    }
    return schema;
  }

  /** PostgreSQL stores the names in lower case, H2 in upper case. */
  private static String upperCase(final String name) {
    return name.toUpperCase(Locale.ROOT);
  }

  /** Overridden to run the same tests on another database. */
  TestDatabase createDatabase() {
    return new TestH2Database();
  }
}
