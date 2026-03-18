/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import liquibase.Liquibase;
import liquibase.change.Change;
import liquibase.change.core.AddColumnChange;
import liquibase.change.core.CreateIndexChange;
import liquibase.change.core.CreateTableChange;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChangelogIdempotencyTest {

  private static final String MASTER_CHANGELOG = "db/changelog/rdbms-exporter/changelog-master.xml";

  private static List<ChangeSet> changeSets;

  @BeforeAll
  static void loadChangelog() throws Exception {
    // Use H2 as a lightweight database for changelog introspection (no DB connection needed)
    final var database = DatabaseFactory.getInstance().getDatabase("h2");

    final var liquibase =
        new Liquibase(MASTER_CHANGELOG, new ClassLoaderResourceAccessor(), database);
    liquibase.setChangeLogParameter("prefix", "");
    liquibase.setChangeLogParameter("userCharColumnSize", 4000);
    liquibase.setChangeLogParameter("errorMessageSize", 4000);
    liquibase.setChangeLogParameter("treePathSize", 4000);

    changeSets = liquibase.getDatabaseChangeLog().getChangeSets();
  }

  @Test
  void shouldHavePreconditionOnCreateTableChangesets() {
    // given the loaded changeSets
    // when filtering for createTable changesets without preConditions
    final var violations =
        changeSets.stream()
            .filter(cs -> hasChangeType(cs, CreateTableChange.class))
            .filter(cs -> cs.getPreconditions() == null)
            .map(ChangeSet::getId)
            .collect(Collectors.toList());

    // then there should be none
    assertThat(violations)
        .as("Changesets with createTable must have preConditions with onFail='MARK_RAN'")
        .isEmpty();
  }

  @Test
  void shouldHavePreconditionOnAddColumnChangesets() {
    // given the loaded changeSets
    // when filtering for addColumn changesets without preConditions
    final var violations =
        changeSets.stream()
            .filter(cs -> hasChangeType(cs, AddColumnChange.class))
            .filter(cs -> cs.getPreconditions() == null)
            .map(ChangeSet::getId)
            .collect(Collectors.toList());

    // then there should be none
    assertThat(violations)
        .as("Changesets with addColumn must have preConditions with onFail='MARK_RAN'")
        .isEmpty();
  }

  @Test
  void shouldHavePreconditionOnCreateIndexChangesets() {
    // given the loaded changeSets
    // when filtering for createIndex changesets without preConditions
    final var violations =
        changeSets.stream()
            .filter(cs -> hasChangeType(cs, CreateIndexChange.class))
            .filter(cs -> cs.getPreconditions() == null)
            .map(ChangeSet::getId)
            .collect(Collectors.toList());

    // then there should be none
    assertThat(violations)
        .as("Changesets with createIndex must have preConditions with onFail='MARK_RAN'")
        .isEmpty();
  }

  @Test
  void shouldHaveSingleDdlChangeForCreateTableAddColumnAndCreateIndexChangesets() {
    // given the loaded changeSets
    // when checking changesets with createTable, addColumn or createIndex for single DDL change
    // note: modifySql directives and preConditions are not in getChanges() - they are tracked
    // separately by Liquibase as SqlVisitors and PreconditionContainer, so getChanges().size()
    // reflects only the actual DDL operations
    final var singleChanges =
        Set.of(CreateTableChange.class, AddColumnChange.class, CreateIndexChange.class);

    final var violations =
        changeSets.stream()
            .filter(
                cs -> singleChanges.stream().anyMatch(changeType -> hasChangeType(cs, changeType)))
            .filter(cs -> cs.getChanges().size() > 1)
            .map(
                cs ->
                    cs.getId()
                        + ": "
                        + cs.getChanges().stream()
                            .map(c -> c.getClass().getSimpleName())
                            .collect(Collectors.joining(", ")))
            .collect(Collectors.toList());

    // then there should be no changesets with more than one DDL change
    assertThat(violations)
        .as(
            "Changesets with createTable, addColumn or createIndex must have only that single DDL change")
        .isEmpty();
  }

  // --- helpers ---

  private static boolean hasChangeType(
      final ChangeSet changeSet, final Class<? extends Change> changeType) {
    return changeSet.getChanges().stream().anyMatch(changeType::isInstance);
  }
}
