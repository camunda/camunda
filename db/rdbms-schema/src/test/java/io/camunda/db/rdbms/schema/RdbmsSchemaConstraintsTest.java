/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import liquibase.Liquibase;
import liquibase.change.Change;
import liquibase.change.ColumnConfig;
import liquibase.change.core.AddColumnChange;
import liquibase.change.core.CreateTableChange;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RdbmsSchemaConstraintsTest {

  private static final String MASTER_CHANGELOG = "db/changelog/rdbms-exporter/changelog-master.xml";

  // INT is an alias for INTEGER on most vendors; prefer INTEGER for new columns.
  private static final Set<String> ALLOWED_COLUMN_TYPES =
      Set.of(
          "BIGINT",
          "NVARCHAR",
          "VARCHAR",
          "SMALLINT",
          "INTEGER",
          "INT",
          "BOOLEAN",
          "CLOB",
          "BLOB",
          "DATETIME",
          "TIMESTAMP WITH TIME ZONE",
          "DOUBLE",
          "NUMBER");

  private static List<ChangeSet> changeSets;

  @BeforeAll
  static void loadChangelog() throws Exception {
    final var database = DatabaseFactory.getInstance().getDatabase("h2");
    final var liquibase =
        new Liquibase(MASTER_CHANGELOG, new ClassLoaderResourceAccessor(), database);
    liquibase.setChangeLogParameter("prefix", "");
    liquibase.setChangeLogParameter("userCharColumnSize", 256);
    liquibase.setChangeLogParameter("errorMessageSize", 4000);
    liquibase.setChangeLogParameter("treePathSize", 4000);
    changeSets = liquibase.getDatabaseChangeLog().getChangeSets();
  }

  @Test
  void shouldUseOnlyApprovedColumnTypes() {
    final var violations = new ArrayList<String>();

    for (final ChangeSet cs : changeSets) {
      for (final Change change : cs.getChanges()) {
        final List<ColumnConfig> columns = columnsOf(change);
        for (final ColumnConfig column : columns) {
          final String rawType = column.getType();
          if (rawType == null) {
            continue;
          }
          final String baseType = normalizeType(rawType);
          if (!ALLOWED_COLUMN_TYPES.contains(baseType)) {
            violations.add(
                "[changeset: %s] Column '%s' uses disallowed type '%s' (base type: '%s')"
                    .formatted(cs.getId(), column.getName(), rawType, baseType));
          }
        }
      }
    }

    assertThat(violations)
        .as(
            """
            One or more Liquibase changesets define columns with types not on the approved list.

            To add a new column type:
              1. Update ALLOWED_COLUMN_TYPES in RdbmsSchemaConstraintsTest.java
              2. Add a comment explaining the use case and cross-vendor compatibility
              3. Get sign-off from the Data Layer team (#team-data-layer on Slack)
              4. Add @camunda/data-layer as a required reviewer on the PR

            See: docs/data-layer/working-with-secondary-storage.md
            """)
        .isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Extracts columns from {@code createTable} and {@code addColumn} changes; empty otherwise. */
  private static List<ColumnConfig> columnsOf(final Change change) {
    if (change instanceof final CreateTableChange c) {
      return c.getColumns();
    }
    if (change instanceof final AddColumnChange c) {
      return new ArrayList<>(c.getColumns());
    }
    return List.of();
  }

  /**
   * Returns the base type by stripping size parameters and upper-casing.
   *
   * <p>Examples: {@code VARCHAR(255)} → {@code VARCHAR}, {@code TIMESTAMP WITH TIME ZONE(3)} →
   * {@code TIMESTAMP WITH TIME ZONE}, {@code NVARCHAR(${userCharColumnSize})} → {@code NVARCHAR}.
   */
  static String normalizeType(final String rawType) {
    final int parenIdx = rawType.indexOf('(');
    final String base = parenIdx >= 0 ? rawType.substring(0, parenIdx) : rawType;
    return base.trim().toUpperCase();
  }
}
