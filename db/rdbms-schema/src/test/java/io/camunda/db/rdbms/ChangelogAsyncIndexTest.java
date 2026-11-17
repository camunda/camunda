/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import liquibase.Liquibase;
import liquibase.change.core.CreateIndexChange;
import liquibase.change.core.CreateTableChange;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class ChangelogAsyncIndexTest {

  private static final String MASTER_CHANGELOG = "db/changelog/rdbms-exporter/changelog-master.xml";
  private static final String ASYNC_INDEX_TEST_MASTER = "db/changelog/async-index-test/master.xml";

  private static List<ChangeSet> loadChangeSets(final String masterChangelog) throws Exception {
    // Use H2 as a lightweight database for changelog introspection (no DB connection needed)
    final var database = DatabaseFactory.getInstance().getDatabase("h2");
    final var liquibase =
        new Liquibase(masterChangelog, new ClassLoaderResourceAccessor(), database);
    liquibase.setChangeLogParameter("prefix", "");
    liquibase.setChangeLogParameter("userCharColumnSize", 4000);
    liquibase.setChangeLogParameter("errorMessageSize", 4000);
    liquibase.setChangeLogParameter("treePathSize", 4000);
    return liquibase.getDatabaseChangeLog().getChangeSets();
  }

  @Test
  void shouldUseAsyncIndexCreationForIndexesOnPreExistingTables() throws Exception {
    // given
    final var changeSets = loadChangeSets(MASTER_CHANGELOG);

    // when checking for locking createIndex changesets that target tables from a different file
    final var violations = findLockingIndexesOnPreExistingTables(changeSets);

    // then there should be none — indexes on pre-existing tables must use concurrent/online SQL
    assertThat(violations)
        .as(
            "Changesets using <createIndex> on a table defined in a different changelog file must "
                + "use async SQL (CONCURRENTLY / WITH (ONLINE = ON) / ONLINE) instead")
        .isEmpty();
  }

  /**
   * Verifies that {@link #shouldUseAsyncIndexCreationForIndexesOnPreExistingTables()} actually
   * catches violations. Loads a deliberately broken fixture changelog that creates an index with
   * standard {@code <createIndex>} on a table defined in a prior file, and asserts the violation is
   * detected.
   */
  @Test
  void shouldDetectLockingIndexViolationInFixture() throws Exception {
    // given: a fixture changelog where v2 adds a locking <createIndex> on a table from v1
    final var fixtureChangeSets = loadChangeSets(ASYNC_INDEX_TEST_MASTER);

    // when
    final var violations = findLockingIndexesOnPreExistingTables(fixtureChangeSets);

    // then: the intentional violation in v2-locking-index.xml is detected
    assertThat(violations).containsExactly("create_test_entity_name_index");
  }

  /**
   * Returns the IDs of changesets that use a locking {@code <createIndex>} on a table that was
   * created in a different changelog file. Such indexes must use database-specific async SQL (e.g.
   * {@code CREATE INDEX CONCURRENTLY} on PostgreSQL) to avoid blocking table access during startup.
   */
  private static List<String> findLockingIndexesOnPreExistingTables(
      final List<ChangeSet> changeSets) {
    // Build a map of tableName -> source file path using createTable changesets
    final Map<String, String> tableCreatedInFile = new HashMap<>();
    for (final var cs : changeSets) {
      for (final var change : cs.getChanges()) {
        if (change instanceof final CreateTableChange createTable) {
          tableCreatedInFile.put(createTable.getTableName(), cs.getFilePath());
        }
      }
    }

    return changeSets.stream()
        .filter(cs -> cs.getChanges().stream().anyMatch(c -> c instanceof CreateIndexChange))
        .filter(
            cs -> {
              final var indexChange =
                  cs.getChanges().stream()
                      .filter(c -> c instanceof CreateIndexChange)
                      .map(c -> (CreateIndexChange) c)
                      .findFirst()
                      .orElseThrow();
              final String tableFile = tableCreatedInFile.get(indexChange.getTableName());
              // A violation only when the table was created in a DIFFERENT file
              return tableFile != null && !tableFile.equals(cs.getFilePath());
            })
        .map(ChangeSet::getId)
        .collect(Collectors.toList());
  }
}
